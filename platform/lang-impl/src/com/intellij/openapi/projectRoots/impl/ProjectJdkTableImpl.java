/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.impl.MessageListenerList;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(
  name="ProjectJdkTable",
  roamingType = RoamingType.DISABLED,
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/jdk.table.xml"
    )}
)
public class ProjectJdkTableImpl extends ProjectJdkTable implements PersistentStateComponent<Element>, ExportableComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl");

  private final List<Sdk> mySdks = new ArrayList<Sdk>();

  private final MessageListenerList<Listener> myListenerList;

  @NonNls private static final String ELEMENT_JDK = "jdk";

  private final Map<String, ProjectJdkImpl> myCachedProjectJdks = new HashMap<String, ProjectJdkImpl>();
  private final MessageBus myMessageBus;

  public ProjectJdkTableImpl() {
    myMessageBus = ApplicationManager.getApplication().getMessageBus();
    myListenerList = new MessageListenerList<Listener>(myMessageBus, JDK_TABLE_TOPIC);
    // support external changes to jdk libraries (Endorsed Standards Override)
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      public void fileCreated(VirtualFileEvent event) {
        updateJdks(event.getFile());
      }

      private void updateJdks(VirtualFile file) {
        if (file.isDirectory() || !StdFileTypes.ARCHIVE.equals(file.getFileType())) {
          // consider only archive files that may contain libraries
          return;
        }
        for (Sdk sdk : mySdks) {
          final SdkType sdkType = sdk.getSdkType();
          if (!(sdkType instanceof JavaSdkType)) {
            continue;
          }
          final VirtualFile home = sdk.getHomeDirectory();
          if (home == null) {
            continue;
          }
          if (VfsUtil.isAncestor(home, file, true)) {
            sdkType.setupSdkPaths(sdk);
            // no need to iterate further assuming the file cannot be under the home of several SDKs
            break;
          }
        }
      }
    });
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("jdk.table")};
  }

  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("sdk.table.settings");
  }

  @Nullable
  public Sdk findJdk(String name) {
    for (Sdk jdk : mySdks) {
      if (Comparing.strEqual(name, jdk.getName())) {
        return jdk;
      }
    }
    return null;
  }

  @Nullable
  public Sdk findJdk(String name, String type) {
    Sdk projectJdk = findJdk(name);
    if (projectJdk != null){
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
      if (Comparing.strEqual(sdkTypeName, sdkType.getName())){
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

  public Sdk[] getAllJdks() {
    return mySdks.toArray(new Sdk[mySdks.size()]);
  }

  public List<Sdk> getSdksOfType(final SdkType type) {
    List<Sdk> result = new ArrayList<Sdk>();
    final Sdk[] sdks = getAllJdks();
    for(Sdk sdk: sdks) {
      if (sdk.getSdkType() == type) {
        result.add(sdk);
      }
    }
    return result;
  }

  @Override
  public Sdk findMostRecentSdkOfType(final SdkType type) {
    return findMostRecentSdk(new Condition<Sdk>() {
      public boolean value(Sdk sdk) {
        return sdk.getSdkType() == type;
      }
    });
  }

  @Override
  public Sdk findMostRecentSdk(Condition<Sdk> condition) {
    Sdk found = null;
    for (Sdk each : getAllJdks()) {
      if (!condition.value(each)) continue;
      if (found == null) {
        found = each;
        continue;
      }
      if (Comparing.compare(each.getVersionString(), found.getVersionString()) > 0) found = each;
    }
    return found;
  }

  public void addJdk(Sdk jdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    mySdks.add(jdk);
    myMessageBus.syncPublisher(JDK_TABLE_TOPIC).jdkAdded(jdk);
  }

  public void removeJdk(Sdk jdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myMessageBus.syncPublisher(JDK_TABLE_TOPIC).jdkRemoved(jdk);
    mySdks.remove(jdk);
  }

  public void updateJdk(Sdk originalJdk, Sdk modifiedJdk) {
    final String previousName = originalJdk.getName();
    final String newName = modifiedJdk.getName();

    ((ProjectJdkImpl)modifiedJdk).copyTo((ProjectJdkImpl)originalJdk);

    if (previousName != null ? !previousName.equals(newName) : newName != null) {
      // fire changes because after renaming JDK its name may match the associated jdk name of modules/project
      myMessageBus.syncPublisher(JDK_TABLE_TOPIC).jdkNameChanged(originalJdk, previousName);
    }
  }

  public void addListener(Listener listener) {
    myListenerList.add(listener);
  }

  public void removeListener(Listener listener) {
    myListenerList.remove(listener);
  }

  public SdkType getDefaultSdkType() {
    return UnknownSdkType.getInstance(null);
  }

  public Sdk createSdk(final String name, final SdkType sdkType) {
    return new ProjectJdkImpl(name, sdkType);
  }

  public void loadState(Element element) {
    mySdks.clear();

    final List children = element.getChildren(ELEMENT_JDK);
    for (final Object aChildren : children) {
      final Element e = (Element)aChildren;
      final ProjectJdkImpl jdk = new ProjectJdkImpl(null, null);
      try {
        jdk.readExternal(e);
      }
      catch (InvalidDataException ex) {
        LOG.error(ex);
      }
      mySdks.add(jdk);
    }
  }

  public Element getState() {
    Element element = new Element("ProjectJdkTableImpl");
    for (Sdk jdk : mySdks) {
      final Element e = new Element(ELEMENT_JDK);
      try {
        ((ProjectJdkImpl)jdk).writeExternal(e);
      }
      catch (WriteExternalException e1) {
        continue;
      }
      element.addContent(e);
    }
    return element;
  }
}
