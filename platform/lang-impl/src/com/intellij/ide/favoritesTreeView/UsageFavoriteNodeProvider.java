/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.NullUsage;
import com.intellij.usages.rules.UsageInFile;
import com.intellij.usages.rules.UsageInFiles;
import com.intellij.util.ProxyComparator;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/6/12
 * Time: 6:51 PM
 */
public class UsageFavoriteNodeProvider extends FavoriteNodeProvider {
  private final static Map<String, TreeSet<WorkingSetSerializable>> ourSerializables =
    new HashMap<>();
  private final static Comparator<VirtualFile> VIRTUAL_FILE_COMPARATOR =
    new ProxyComparator<>(new Convertor<VirtualFile, String>() {
      @Override
      public String convert(VirtualFile o) {
        return o.getPath();
      }
    });
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.favoritesTreeView.UsageFavoriteNodeProvider");

  static {
    final TreeSet<WorkingSetSerializable> usageSet = createSet();
    final UsageSerializable serializable = new UsageSerializable();
    ourSerializables.put(serializable.getId(), usageSet);
    usageSet.add(serializable);

    final TreeSet<WorkingSetSerializable> fileSet = createSet();
    final FileSerializable fileSerializable = new FileSerializable();
    ourSerializables.put(fileSerializable.getId(), fileSet);
    fileSet.add(fileSerializable);

    final TreeSet<WorkingSetSerializable> noteSet = createSet();
    final NoteSerializable noteSerializable = new NoteSerializable();
    ourSerializables.put(noteSerializable.getId(), noteSet);
    noteSet.add(noteSerializable);
  }

  private static TreeSet<WorkingSetSerializable> createSet() {
    return new TreeSet<>((o1, o2) -> {
      assert o1.getId().equals(o1.getId());
      return Comparing.compare(o1.getVersion(), o2.getVersion());
    });
  }

  @Override
  public Collection<AbstractTreeNode> getFavoriteNodes(DataContext context, ViewSettings viewSettings) {
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return null;
    }
    final Usage[] usages = UsageView.USAGES_KEY.getData(context);
    if (usages != null) {

      final List<AbstractTreeNode> result = new SmartList<>();
      final MultiMap<VirtualFile, Usage> map = new MultiMap<>();
      final List<Usage> nonMapped = new ArrayList<>();
      for (Usage usage : usages) {
        if (usage instanceof UsageInFile) {
          map.putValue(((UsageInFile)usage).getFile(), usage);
        }
        else if (usage instanceof UsageInFiles) {
          final VirtualFile[] files = ((UsageInFiles)usage).getFiles();
          for (VirtualFile file : files) {
            map.putValue(file, usage);
          }
        }
        else {
          nonMapped.add(usage);
        }
      }

      final TreeSet<VirtualFile> keys = new TreeSet<>(VIRTUAL_FILE_COMPARATOR);
      keys.addAll(map.keySet());
      for (VirtualFile key : keys) {
        final FileGroupingProjectNode grouping = new FileGroupingProjectNode(project, new File(key.getPath()), viewSettings);
        result.add(grouping);
        final Collection<Usage> subUsages = map.get(key);
        for (Usage usage : subUsages) {
          if (usage instanceof UsageInfo2UsageAdapter) {
            final UsageProjectTreeNode node =
              new UsageProjectTreeNode(project, ((UsageInfo2UsageAdapter)usage).getUsageInfo(), viewSettings);
            grouping.addChild(node);
          }
          else if (NullUsage.INSTANCE.equals(usage)) {
            continue;
          }
          else {
            grouping.addChild(new NoteProjectNode(project, new NoteNode(usage.getPresentation().getPlainText(), true), viewSettings));
          }
        }
      }
      for (Usage usage : nonMapped) {
        if (usage instanceof UsageInfo2UsageAdapter) {
          final UsageProjectTreeNode node =
            new UsageProjectTreeNode(project, ((UsageInfo2UsageAdapter)usage).getUsageInfo(), viewSettings);
          result.add(node);
        }
        else if (NullUsage.INSTANCE.equals(usage)) {
          continue;
        }
        else {
          result.add(new NoteProjectNode(project, new NoteNode(usage.getPresentation().getPlainText(), true), viewSettings));
        }
      }

      return result;
    }
    return null;
  }

  @Override
  public AbstractTreeNode createNode(Project project, Object element, ViewSettings viewSettings) {
    if (element instanceof UsageInfo) {
      return new UsageProjectTreeNode(project, (UsageInfo)element, viewSettings);
    }
    else if (element instanceof InvalidUsageNoteNode) {
      return new InvalidUsageNoteProjectNode(project, (InvalidUsageNoteNode)element, viewSettings);
    }
    else if (element instanceof NoteNode) {
      return new NoteProjectNode(project, (NoteNode)element, viewSettings);
    }
    else if (element instanceof File) {
      return new FileGroupingProjectNode(project, (File)element, viewSettings);
    }
    return super.createNode(project, element, viewSettings);
  }

  @Override
  public PsiElement getPsiElement(Object element) {
    if (element instanceof UsageInfo) {
      return ((UsageInfo)element).getElement();
    }
    return super.getPsiElement(element);
  }

  @Override
  public boolean elementContainsFile(Object element, VirtualFile vFile) {
    return false;
  }

  @Override
  public int getElementWeight(Object element, boolean isSortByType) {
    return 0;
  }

  @Override
  public String getElementLocation(Object element) {
    if (element instanceof UsageInfo) {
      final PsiElement psiElement = ((UsageInfo)element).getElement();
      final PsiFile file = psiElement.getContainingFile();
      /*if (parent != null) {
        return ClassPresentationUtil.getNameForClass(parent, true);
      }*/
      return file.getPresentation().getPresentableText();//+-      // todo do smthg for invalid usage
    }
    else if (element instanceof File) {
      return ((File)element).getParent();
    }
    return null;
  }

  @Override
  public boolean isInvalidElement(Object element) {
    /*if (element instanceof UsageInfo) {
      return ((UsageInfo)element).getElement().isValid();
    } else if (element instanceof InvalidUsageNoteNode) {
      return true;
    } */
    return false;
  }

  @NotNull
  @Override
  public String getFavoriteTypeId() {
    return "usage";
  }

  @Override
  public String getElementUrl(Object element) {
    //if (element instanceof UsageInfo) {
    final TreeSet<WorkingSetSerializable> serializables = ourSerializables.get(element.getClass().getName());
    if (serializables != null && !serializables.isEmpty()) {
      final WorkingSetSerializable last = serializables.last();
      //final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try {
        //final ObjectOutputStream os = new ObjectOutputStream(baos);
        final StringBuilder sb = new StringBuilder();
        sb.append(last.getId());
        sb.append(' ');
        sb.append("" + last.getVersion());
        sb.append(' ');

        //os.writeUTF(last.getId());
        //os.writeInt(last.getVersion());
        last.serializeMe(element, sb);
        //os.close();
        //final byte[] bytes = baos.toByteArray();
        return sb.toString();
        //return new String(bytes, 4, bytes.length - 4);
      }
      catch (IOException e) {
        LOG.info(e);
        return null;
      }
    }
    //}
    return null;
  }

  @Override
  public String getElementModuleName(Object element) {
    if (element instanceof UsageInfo) {
      Module module = ModuleUtil.findModuleForPsiElement(((UsageInfo)element).getElement());
      return module != null ? module.getName() : null;
    }
    return null;
  }

  @Override
  public Object[] createPathFromUrl(Project project, String url, String moduleName) {
    try {
      //final byte[] bytes = url.getBytes(CharsetToolkit.UTF8_CHARSET);
      /*final byte[] wrapped = new byte[bytes.length + 4];
      final ByteArrayOutputStream bas = new ByteArrayOutputStream();
      final ObjectOutputStream oos = new ObjectOutputStream(bas);
      oos.close();
      final byte[] header = bas.toByteArray();
      System.arraycopy(header, 0, wrapped, 0, 4);
      System.arraycopy(bytes, 0, wrapped, 0, bytes.length);*/

      //ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(bytes));
      final List<String> parts = StringUtil.split(url, " ", true);
      if (parts.size() < 3) return null;

      final String id = parts.get(0);
      final TreeSet<WorkingSetSerializable> set = ourSerializables.get(id);
      if (set != null && !set.isEmpty()) {
        final int version = Integer.parseInt(parts.get(1));
        final String cut = StringUtil.join(parts.subList(2, parts.size()), " ");
        for (Iterator<WorkingSetSerializable> iterator = set.descendingIterator(); iterator.hasNext(); ) {
          WorkingSetSerializable serializable = iterator.next();
          if (serializable.getVersion() == version) {
            return readWithSerializable(project, url, cut, serializable);
          }
        }
        readWithSerializable(project, url, cut, set.last());
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return null;
  }

  private Object[] readWithSerializable(Project project, String url, String is, WorkingSetSerializable serializable)
    throws IOException {
    Object obj = serializable.deserializeMe(project, is);
    if (obj == null) {
      obj = serializable.deserializeMeInvalid(project, is);
    }
    return obj == null ? null : new Object[]{obj};
    /*Object obj = serializable.deserializeMe(project, is);
    if (obj == null) {
      is.close();
      is = new ObjectInputStream(new ByteArrayInputStream(url.getBytes(CharsetToolkit.UTF8_CHARSET)));
      is.readUTF();
      is.readInt();
      obj = serializable.deserializeMeInvalid(project, is);
    }
    if (obj != null) {
      return new Object[]{obj};
    } else {
      return null;
    }*/
  }
}
