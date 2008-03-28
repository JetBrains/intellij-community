/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilderDriver;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.xml.impl.DomApplicationComponent;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author peter
 */
public class DomFileIndex extends ScalarIndexExtension<String>{
  public static final ID<String,Void> NAME = ID.create("DomFileIndex");
  private static final FileBasedIndex.InputFilter INPUT_FILTER = new FileBasedIndex.InputFilter() {
    public boolean acceptInput(final VirtualFile file) {
      return file.getFileType() == StdFileTypes.XML;
    }
  };
  private final DataIndexer<String,Void, FileContent> myDataIndexer;

  public DomFileIndex() {
    myDataIndexer = new DataIndexer<String, Void, FileContent>() {
      public Map<String, Void> map(final FileContent inputData) {
        final CharSequence content = inputData.getContentAsText();
        final Ref<String> rootTagName = Ref.create(null);
        final Set<String> namespaces = new THashSet<String>();
        try {
          new XmlBuilderDriver(content).build(new XmlBuilder() {
            public void doctype(@Nullable final CharSequence publicId, @Nullable final CharSequence systemId, final int startOffset, final int endOffset) {
              if (publicId != null) {
                namespaces.add(publicId.toString());
              }
              if (systemId != null) {
                namespaces.add(systemId.toString());
              }
            }

            public ProcessingOrder startTag(final CharSequence localName,
                                            final String namespace,
                                            final int startoffset,
                                            final int endoffset,
                                            final int headerEndOffset) {
              rootTagName.set(localName.toString());
              ContainerUtil.addIfNotNull(namespace, namespaces);
              throw new RootTagReachedException();
            }

            public void endTag(final CharSequence localName, final String namespace, final int startoffset, final int endoffset) {
              throw new UnsupportedOperationException("Method endTag is not yet implemented in " + getClass().getName());
            }

            public void attribute(final CharSequence name, final CharSequence value, final int startoffset, final int endoffset) {
            }

            public void textElement(final CharSequence display, final CharSequence physical, final int startoffset, final int endoffset) {
            }

            public void entityRef(final CharSequence ref, final int startOffset, final int endOffset) {
            }
          });
        }
        catch (RootTagReachedException e) {
        }
        final String tagName = rootTagName.get();
        if (StringUtil.isNotEmpty(tagName)) {
          final THashMap<String, Void> result = new THashMap<String, Void>();
          final DomApplicationComponent component = DomApplicationComponent.getInstance();
          for (final DomFileDescription description : component.getFileDescriptions(tagName)) {
            final String[] strings = description.getAllPossibleRootTagNamespaces();
            if (strings.length == 0 || ContainerUtil.intersects(Arrays.asList(strings), namespaces)) {
              result.put(description.getClass().getName(), null);
            }
          }
          for (final DomFileDescription description : component.getAcceptingOtherRootTagNameDescriptions()) {
            final String[] strings = description.getAllPossibleRootTagNamespaces();
            if (strings.length == 0 || ContainerUtil.intersects(Arrays.asList(strings), namespaces)) {
              result.put(description.getClass().getName(), null);
            }
          }
          return result;
        }
        return Collections.emptyMap();
      }
    };
  }

  public ID<String, Void> getName() {
    return NAME;
  }

  public static List<VirtualFile> getAllFiles(Class<? extends DomFileDescription> description, Project project, final GlobalSearchScope scope) {
    return ContainerUtil.findAll(getAllFiles(description, project), new Condition<VirtualFile>() {
      public boolean value(final VirtualFile file) {
        return scope.contains(file);
      }
    });
  }

  public static Collection<VirtualFile> getAllFiles(Class<? extends DomFileDescription> description, Project project) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, description.getName(), project);
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  public PersistentEnumerator.DataDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return INPUT_FILTER;
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public int getVersion() {
    return 0;
  }

  private static class RootTagReachedException extends RuntimeException{
  }
}
