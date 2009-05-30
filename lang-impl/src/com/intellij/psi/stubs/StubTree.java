/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StubTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.StubTree");

  private final PsiFileStub myRoot;
  private final List<StubElement<?>> myPlainList = new ArrayList<StubElement<?>>();

  public StubTree(final PsiFileStub root) {
    myRoot = root;
    enumerateStubs(root, myPlainList);
  }

  private static void enumerateStubs(final StubElement<?> root, final List<StubElement<?>> result) {
    ((StubBase)root).id = result.size();
    result.add(root);
    for (StubElement child : root.getChildrenStubs()) {
      enumerateStubs(child, result);
    }
  }

  public PsiFileStub getRoot() {
    return myRoot;
  }

  public List<StubElement<?>> getPlainList() {
    return myPlainList;
  }

  @NotNull
  public Map<StubIndexKey, Map<Object, TIntArrayList>> indexStubTree() {
    final Map<StubIndexKey, Map<Object, TIntArrayList>> result = new HashMap<StubIndexKey, Map<Object, TIntArrayList>>();

    for (int i = 0; i < myPlainList.size(); i++) {
      StubElement<?> stub = myPlainList.get(i);
      final StubSerializer serializer = SerializationManager.getInstance().getSerializer(stub);
      final int stubIdx = i;
      serializer.indexStub(stub, new IndexSink() {
        public void occurrence(@NotNull final StubIndexKey indexKey, @NotNull final Object value) {
          Map<Object, TIntArrayList> map = result.get(indexKey);
          if (map == null) {
            map = new HashMap<Object, TIntArrayList>();
            result.put(indexKey, map);
          }

          TIntArrayList list = map.get(value);
          if (list == null) {
            list = new TIntArrayList();
            map.put(value, list);
          }
          list.add(stubIdx);
        }
      });
    }

    return result;
  }

  @Nullable
  public static StubTree readFromVFile(Project project, final VirtualFile vFile) {
    if (DumbService.getInstance().isDumb()) {
      try {
        final FileContent fc = new FileContent(vFile, vFile.contentsToByteArray());
        fc.putUserData(FileBasedIndex.PROJECT, project);
        final StubElement element = StubUpdatingIndex.buildStubTree(fc);
        if (element instanceof PsiFileStub) {
          return new StubTree((PsiFileStub)element);
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
      return null;
    }

    final int id = Math.abs(FileBasedIndex.getFileId(vFile));
    if (id > 0) {
      final List<SerializedStubTree> datas = FileBasedIndex.getInstance().getValues(StubUpdatingIndex.INDEX_ID, id, GlobalSearchScope.allScope(project));
      final int size = datas.size();

      if (size == 1) {
        StubElement stub = datas.get(0).getStub();
        return new StubTree((PsiFileStub)stub);
      }
      else if (size != 0) {
        LOG.error("Twin stubs: " + vFile.getPresentableUrl() + " has " + size + " stub versions. Should only have one. id=" + id);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final Document doc = FileDocumentManager.getInstance().getCachedDocument(vFile);
            if (doc != null) {
              FileDocumentManager.getInstance().saveDocument(doc);
            }
          }
        }, ModalityState.NON_MODAL);

        FileBasedIndex.getInstance().requestReindex(vFile);
      }
    }

    return null;
  }
}
