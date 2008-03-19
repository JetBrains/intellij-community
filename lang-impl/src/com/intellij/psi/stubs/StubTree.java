/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StubTree {
  private final PsiFileStub myRoot;
  private final List<StubElement<?>> myPlainList = new ArrayList<StubElement<?>>();

  public StubTree(final PsiFileStub root) {
    myRoot = root;
    enumerateStubs(root, myPlainList);
  }

  private static void enumerateStubs(final StubElement<?> root, final List<StubElement<?>> result) {
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

  public Map<StubIndexKey, Map<Object, Integer>> indexStubTree() {
    final Map<StubIndexKey, Map<Object, Integer>> result = new HashMap<StubIndexKey, Map<Object, Integer>>();

    for (int i = 0; i < myPlainList.size(); i++) {
      StubElement<?> stub = myPlainList.get(i);
      final StubSerializer serializer = SerializationManager.getInstance().getSerializer(stub);
      final int stubIdx = i;
      serializer.indexStub(stub, new IndexSink() {
        public void occurence(final StubIndexKey indexKey, final Object value) {
          Map<Object, Integer> map = result.get(indexKey);
          if (map == null) {
            map = new HashMap<Object, Integer>();
            result.put(indexKey, map);
          }
          map.put(value, stubIdx);
        }
      });
    }

    return result;
  }

  @Nullable
  public static StubTree readFromVFile(final VirtualFile vFile, Project project) {
    final int id = Math.abs(FileBasedIndex.getFileId(vFile));
    if (id > 0) {
      final List<SerializedStubTree> datas = FileBasedIndex.getInstance().getValues(StubUpdatingIndex.INDEX_ID, id, project);
      if (datas.size() == 1) {
        StubElement stub = datas.get(0).getStub();
        return new StubTree((PsiFileStub)stub);
      }
    }

    return null;
  }
}