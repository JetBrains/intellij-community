/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.impl.MessageListenerList;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

@State(
  name = "ProjectJdkTable",
  storages = @Storage(value = "jdk.table.xml", roamingType = RoamingType.DISABLED)
)
public class ProjectJdkTableImpl extends ProjectJdkTable implements ExportableComponent, PersistentStateComponent<Element> {
  private final List<Sdk> mySdks = new ArrayList<>();

  private final MessageListenerList<Listener> myListenerList;

  @NonNls private static final String ELEMENT_JDK = "jdk";

  private final Map<String, ProjectJdkImpl> myCachedProjectJdks = new HashMap<>();
  private final MessageBus myMessageBus;

  public ProjectJdkTableImpl() {
    myMessageBus = ApplicationManager.getApplication().getMessageBus();
    myListenerList = new MessageListenerList<>(myMessageBus, JDK_TABLE_TOPIC);
    // support external changes to jdk libraries (Endorsed Standards Override)
    final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      private FileTypeManager myFileTypeManager = FileTypeManager.getInstance();

      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        if (!events.isEmpty()) {
          final Set<Sdk> affected = new SmartHashSet<>();
          for (VFileEvent event : events) {
            addAffectedJavaSdk(event, affected);
          }
          if (!affected.isEmpty()) {
            for (Sdk sdk : affected) {
              ((SdkType)sdk.getSdkType()).setupSdkPaths(sdk);
            }
          }
        }
      }

      private void addAffectedJavaSdk(VFileEvent event, Set<Sdk> affected) {
        final VirtualFile file = event.getFile();
        String fileName = null;
        if (file != null && file.isValid()) {
          if (file.isDirectory()) {
            return;
          }
          fileName = file.getName();
        }
        final String eventPath = event.getPath();
        if (fileName == null) {
          fileName = VfsUtil.extractFileName(eventPath);
        }
        if (fileName != null) {
          // avoid calling getFileType() because it will try to detect file type from content for unknown/text file types
          // consider only archive files that may contain libraries
          if (!FileTypes.ARCHIVE.equals(myFileTypeManager.getFileTypeByFileName(fileName))) {
            return;
          }
        }

        for (Sdk sdk : mySdks) {
          if (sdk.getSdkType() instanceof JavaSdkType && !affected.contains(sdk)) {
            final String homePath = sdk.getHomePath();
            if (!StringUtil.isEmpty(homePath) && FileUtil.isAncestor(homePath, eventPath, true)) {
              affected.add(sdk);
            }
          }
        }
      }
    });
  }

  @Override
  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("jdk.table")};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("sdk.table.settings");
  }

  @Override
  @Nullable
  public Sdk findJdk(String name) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, len = mySdks.size(); i < len; ++i) { // avoid foreach,  it instantiates ArrayList$Itr, this traversal happens very often
      final Sdk jdk = mySdks.get(i);
      if (Comparing.strEqual(name, jdk.getName())) {
        return jdk;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Sdk findJdk(String name, String type) {
    Sdk projectJdk = findJdk(name);
    if (projectJdk != null) {
      return projectJdk;
    }
    final String sdkTypeName = getSdkTypeName(type);
    final String uniqueName = sdkTypeName + "." + name;
    projectJdk = myCachedProjectJdks.get(uniqueName);
    if (projectJdk != null) return projectJdk;

    @NonNls final String jdkPrefix = "jdk.";
    final String jdkPath = System.getProperty(jdkPrefix + name);
    if (jdkPath == null) return null;

    final SdkType[] sdkTypes = SdkType.getAllTypes();
    for (SdkType sdkType : sdkTypes) {
      if (Comparing.strEqual(sdkTypeName, sdkType.getName())) {
        if (sdkType.isValidSdkHome(jdkPath)) {
          ProjectJdkImpl projectJdkImpl = new ProjectJdkImpl(name, sdkType);
          projectJdkImpl.setHomePath(jdkPath);
          sdkType.setupSdkPaths(projectJdkImpl);
          myCachedProjectJdks.put(uniqueName, projectJdkImpl);
          return projectJdkImpl;
        }
        break;
      }
    }
    return null;
  }

  protected String getSdkTypeName(final String type) {
    return type;
  }

  @Override
  public Sdk[] getAllJdks() {
    return mySdks.toArray(new Sdk[mySdks.size()]);
  }

  @Override
  public List<Sdk> getSdksOfType(final SdkTypeId type) {
    List<Sdk> result = new ArrayList<>();
    final Sdk[] sdks = getAllJdks();
    for (Sdk sdk : sdks) {
      if (sdk.getSdkType() == type) {
        result.add(sdk);
      }
    }
    return result;
  }

  @Override
  public void addJdk(Sdk jdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    mySdks.add(jdk);
    myMessageBus.syncPublisher(JDK_TABLE_TOPIC).jdkAdded(jdk);
  }

  @Override
  public void removeJdk(Sdk jdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myMessageBus.syncPublisher(JDK_TABLE_TOPIC).jdkRemoved(jdk);
    mySdks.remove(jdk);
  }

  @Override
  public void updateJdk(Sdk originalJdk, Sdk modifiedJdk) {
    final String previousName = originalJdk.getName();
    final String newName = modifiedJdk.getName();

    ((ProjectJdkImpl)modifiedJdk).copyTo((ProjectJdkImpl)originalJdk);

    if (!previousName.equals(newName)) {
      // fire changes because after renaming JDK its name may match the associated jdk name of modules/project
      myMessageBus.syncPublisher(JDK_TABLE_TOPIC).jdkNameChanged(originalJdk, previousName);
    }
  }

  @Override
  public void addListener(Listener listener) {
    myListenerList.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    myListenerList.remove(listener);
  }

  @Override
  public SdkTypeId getDefaultSdkType() {
    return UnknownSdkType.getInstance("");
  }

  @Override
  public SdkTypeId getSdkTypeByName(@NotNull String sdkTypeName) {
    return findSdkTypeByName(sdkTypeName);
  }

  public static SdkTypeId findSdkTypeByName(@NotNull String sdkTypeName) {
    final SdkType[] allSdkTypes = SdkType.getAllTypes();
    for (final SdkType type : allSdkTypes) {
      if (type.getName().equals(sdkTypeName)) {
        return type;
      }
    }
    return UnknownSdkType.getInstance(sdkTypeName);
  }

  @Override
  public Sdk createSdk(final String name, final SdkTypeId sdkType) {
    return new ProjectJdkImpl(name, sdkType);
  }

  @Override
  public void loadState(Element element) {
    mySdks.clear();

    for (Element child : element.getChildren(ELEMENT_JDK)) {
      ProjectJdkImpl jdk = new ProjectJdkImpl(null, null);
      jdk.readExternal(child, this);
      mySdks.add(jdk);
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    for (Sdk jdk : mySdks) {
      Element e = new Element(ELEMENT_JDK);
      ((ProjectJdkImpl)jdk).writeExternal(e);
      element.addContent(e);
    }
    return element;
  }
}
