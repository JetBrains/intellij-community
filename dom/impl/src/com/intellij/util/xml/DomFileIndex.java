/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.xml.impl.DomApplicationComponent;
import gnu.trove.THashMap;
import gnu.trove.THashSet;

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
        final Set<String> namespaces = new THashSet<String>();
        final XmlFileHeader header = NanoXmlUtil.parseHeader(inputData.getFile());
        ContainerUtil.addIfNotNull(header.getPublicId(), namespaces);
        ContainerUtil.addIfNotNull(header.getSystemId(), namespaces);
        ContainerUtil.addIfNotNull(header.getRootTagNamespace(), namespaces);
        final String tagName = header.getRootTagLocalName();
        if (StringUtil.isNotEmpty(tagName)) {
          final THashMap<String, Void> result = new THashMap<String, Void>();
          final DomApplicationComponent component = DomApplicationComponent.getInstance();
          for (final DomFileDescription description : component.getFileDescriptions(tagName)) {
            final String[] strings = description.getAllPossibleRootTagNamespaces();
            if (strings.length == 0 || ContainerUtil.intersects(Arrays.asList(strings), namespaces)) {
              result.put(description.getRootElementClass().getName(), null);
            }
          }
          for (final DomFileDescription description : component.getAcceptingOtherRootTagNameDescriptions()) {
            final String[] strings = description.getAllPossibleRootTagNamespaces();
            if (strings.length == 0 || ContainerUtil.intersects(Arrays.asList(strings), namespaces)) {
              result.put(description.getRootElementClass().getName(), null);
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
