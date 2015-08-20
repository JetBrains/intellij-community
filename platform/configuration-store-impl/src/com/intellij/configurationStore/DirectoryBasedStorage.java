/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.configurationStore;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.StateSplitter;
import com.intellij.openapi.components.StateSplitterEx;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.*;
import com.intellij.openapi.components.store.ReadOnlyModificationException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.LineSeparator;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.components.impl.stores.StateMap.getNewByteIfDiffers;

public class DirectoryBasedStorage extends StateStorageBase<DirectoryStorageData> {
  private final File myDir;
  private volatile VirtualFile myVirtualFile;
  @SuppressWarnings("deprecation")
  private final StateSplitter mySplitter;

  private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;

  public DirectoryBasedStorage(@Nullable TrackingPathMacroSubstitutor pathMacroSubstitutor, @NotNull File dir, @SuppressWarnings("deprecation") @NotNull StateSplitter splitter) {
    myPathMacroSubstitutor = pathMacroSubstitutor;
    myDir = dir;
    mySplitter = splitter;
  }

  public void setVirtualDir(@Nullable VirtualFile dir) {
    myVirtualFile = dir;
  }

  @Override
  public void analyzeExternalChangesAndUpdateIfNeed(@NotNull Set<String> componentNames) {
    // todo reload only changed file, compute diff
    DirectoryStorageData oldData = storageDataRef.get();
    DirectoryStorageData newData = loadData();
    storageDataRef.set(newData);
    if (oldData == null) {
      componentNames.addAll(newData.getComponentNames());
    }
    else {
      componentNames.addAll(oldData.getComponentNames());
      componentNames.addAll(newData.getComponentNames());
    }
  }

  @Nullable
  @Override
  protected Element getStateAndArchive(@NotNull DirectoryStorageData storageData, Object component, @NotNull String componentName) {
    return getCompositeStateAndArchive(storageData, componentName, mySplitter);
  }

  @Nullable
  private static Element getCompositeStateAndArchive(@NotNull DirectoryStorageData storageData, @NotNull String componentName, @SuppressWarnings("deprecation") @NotNull StateSplitter splitter) {
    StateMap fileToState = storageData.states.get(componentName);
    Element state = new Element(StateMap.COMPONENT);
    if (fileToState == null || fileToState.isEmpty()) {
      return state;
    }

    if (splitter instanceof StateSplitterEx) {
      for (String fileName : fileToState.keys()) {
        Element subState = fileToState.getStateAndArchive(fileName);
        if (subState == null) {
          return null;
        }
        ((StateSplitterEx)splitter).mergeStateInto(state, subState);
      }
    }
    else {
      List<Element> subElements = new SmartList<Element>();
      for (String fileName : fileToState.keys()) {
        Element subState = fileToState.getStateAndArchive(fileName);
        if (subState == null) {
          return null;
        }
        subElements.add(subState);
      }

      if (!subElements.isEmpty()) {
        splitter.mergeStatesInto(state, subElements.toArray(new Element[subElements.size()]));
      }
    }
    return state;
  }

  @NotNull
  @Override
  protected DirectoryStorageData loadData() {
    Map map = DirectoryStorageUtil.loadFrom(getVirtualFile(), myPathMacroSubstitutor);
    //noinspection unchecked
    return DirectoryStorageData.fromMap(map);
  }

  @Nullable
  private VirtualFile getVirtualFile() {
    VirtualFile virtualFile = myVirtualFile;
    if (virtualFile == null) {
      myVirtualFile = virtualFile = LocalFileSystem.getInstance().findFileByIoFile(myDir);
    }
    return virtualFile;
  }

  @Override
  @Nullable
  public ExternalizationSession startExternalization() {
    return checkIsSavingDisabled() ? null : new MySaveSession(this, getStorageData());
  }

  private static class MySaveSession extends SaveSessionBase {
    private final DirectoryBasedStorage storage;
    private final DirectoryStorageData originalStorageData;
    private Map<String, Map<String, Object>> copiedStorageData;

    private final Set<String> dirtyFileNames = new SmartHashSet<String>();
    private final Set<String> removedFileNames = new SmartHashSet<String>();

    private MySaveSession(@NotNull DirectoryBasedStorage storage, @NotNull DirectoryStorageData storageData) {
      this.storage = storage;
      originalStorageData = storageData;
    }

    @NotNull
    private static String[] getFileNames(@NotNull DirectoryStorageData directoryStorageData, @NotNull String componentName) {
      StateMap fileToState = directoryStorageData.states.get(componentName);
      return fileToState == null || fileToState.isEmpty() ? ArrayUtil.EMPTY_STRING_ARRAY : fileToState.keys();
    }

    @Override
    protected void setSerializedState(@NotNull Object component, @NotNull String componentName, @Nullable Element element) {
      ContainerUtil.addAll(removedFileNames, getFileNames(originalStorageData, componentName));
      if (JDOMUtil.isEmpty(element)) {
        doSetState(componentName, null, null);
      }
      else {
        for (Pair<Element, String> pair : storage.mySplitter.splitState(element)) {
          removedFileNames.remove(pair.second);
          doSetState(componentName, pair.second, pair.first);
        }

        if (!removedFileNames.isEmpty()) {
          for (String fileName : removedFileNames) {
            doSetState(componentName, fileName, null);
          }
        }
      }
    }

    private void doSetState(@NotNull String componentName, @Nullable String fileName, @Nullable Element subState) {
      if (copiedStorageData == null) {
        copiedStorageData = setStateAndCloneIfNeed(componentName, fileName, subState, originalStorageData);
        if (copiedStorageData != null && fileName != null) {
          dirtyFileNames.add(fileName);
        }
      }
      else if (DirectoryStorageUtil.setState(copiedStorageData, componentName, fileName, subState) != null && fileName != null) {
        dirtyFileNames.add(fileName);
      }
    }

    @Override
    @Nullable
    public SaveSession createSaveSession() {
      return storage.checkIsSavingDisabled() || copiedStorageData == null ? null : this;
    }

    @Override
    public void save() throws IOException {
      VirtualFile dir = storage.getVirtualFile();
      if (copiedStorageData.isEmpty()) {
        if (dir != null && dir.exists()) {
          StorageUtil.deleteFile(this, dir);
        }
        setStorageData();
        return;
      }

      if (dir == null || !dir.isValid()) {
        dir = StorageUtil.createDir(storage.myDir, this);
      }

      if (!dirtyFileNames.isEmpty()) {
        saveStates(dir);
      }
      if (dir.exists() && !removedFileNames.isEmpty()) {
        deleteFiles(dir);
      }

      storage.myVirtualFile = dir;
      setStorageData();
    }

    private void setStorageData() {
      Map data = copiedStorageData;
      //noinspection unchecked
      storage.storageDataRef.set(DirectoryStorageData.fromMap(data));
    }

    private void saveStates(@NotNull final VirtualFile dir) {
      final Element storeElement = new Element(StateMap.COMPONENT);

      for (Map.Entry<String, Map<String, Object>> componentNameToFileNameToStates : copiedStorageData.entrySet()) {
        for (Map.Entry<String, Object> entry : componentNameToFileNameToStates.getValue().entrySet()) {
          String fileName = entry.getKey();
          Object state = entry.getValue();

          if (!dirtyFileNames.contains(fileName)) {
            return;
          }

          Element element = null;
          try {
            element = StateMap.stateToElement(fileName, state, Collections.<String, Element>emptyMap());
            if (storage.myPathMacroSubstitutor != null) {
              storage.myPathMacroSubstitutor.collapsePaths(element);
            }

            storeElement.setAttribute(StateMap.NAME, componentNameToFileNameToStates.getKey());
            storeElement.addContent(element);

            VirtualFile file = StorageUtil.getFile(fileName, dir, this);
            // we don't write xml prolog due to historical reasons (and should not in any case)
            StorageUtil.writeFile(null, this, file, storeElement,
                                  LineSeparator.fromString(file.exists() ? StorageUtil.loadFile(file).second : SystemProperties.getLineSeparator()), false);
          }
          catch (IOException e) {
            LOG.error(e);
          }
          finally {
            if (element != null) {
              element.detach();
            }
          }
        }
      }
    }

    private void deleteFiles(@NotNull VirtualFile dir) throws IOException {
      AccessToken token = WriteAction.start();
      try {
        for (VirtualFile file : dir.getChildren()) {
          if (removedFileNames.contains(file.getName())) {
            try {
              file.delete(this);
            }
            catch (FileNotFoundException e) {
              throw new ReadOnlyModificationException(file, e, null);
            }
          }
        }
      }
      finally {
        token.finish();
      }
    }
  }

  @Nullable
  public static Map<String, Map<String, Object>> setStateAndCloneIfNeed(@NotNull String componentName,
                                                                        @Nullable String fileName,
                                                                        @Nullable Element newState,
                                                                        @NotNull DirectoryStorageData storageData) {
    StateMap fileToState = storageData.states.get(componentName);
    Object oldState = fileToState == null || fileName == null ? null : fileToState.get(fileName);
    if (fileName == null || newState == null || JDOMUtil.isEmpty(newState)) {
      if (fileName == null) {
        if (fileToState == null) {
          return null;
        }
      }
      else if (oldState == null) {
        return null;
      }

      Map<String, Map<String, Object>> newStorageData = storageData.toMap();
      if (fileName == null) {
        newStorageData.remove(componentName);
      }
      else {
        Map<String, Object> clonedFileToState = newStorageData.get(componentName);
        if (clonedFileToState.size() == 1) {
          newStorageData.remove(componentName);
        }
        else {
          clonedFileToState.remove(fileName);
          if (clonedFileToState.isEmpty()) {
            newStorageData.remove(componentName);
          }
        }
      }
      return newStorageData;
    }

    byte[] newBytes = null;
    if (oldState instanceof Element) {
      if (JDOMUtil.areElementsEqual((Element)oldState, newState)) {
        return null;
      }
    }
    else if (oldState != null) {
      newBytes = getNewByteIfDiffers(componentName, newState, (byte[])oldState);
      if (newBytes == null) {
        return null;
      }
    }

    Map<String, Map<String, Object>> newStorageData = storageData.toMap();
    put(newStorageData, componentName, fileName, newBytes == null ? newState : newBytes);
    return newStorageData;
  }

  private static void put(@NotNull Map<String, Map<String, Object>> states, @NotNull String componentName, @NotNull String fileName, @NotNull Object state) {
    Map<String, Object> fileToState = states.get(componentName);
    if (fileToState == null) {
      fileToState = new THashMap<String, Object>();
      states.put(componentName, fileToState);
    }
    fileToState.put(fileName, state);
  }
}
