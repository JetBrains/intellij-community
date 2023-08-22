// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

@State(
  name = "ProjectJdkTable",
  storages = @Storage(value = "jdk.table.xml", roamingType = RoamingType.DISABLED, useSaveThreshold = ThreeState.NO)
)
public class ProjectJdkTableImpl extends ProjectJdkTable implements ExportableComponent, PersistentStateComponent<Element> {
  private final List<Sdk> mySdks = new ArrayList<>();

  @NonNls
  private static final String ELEMENT_JDK = "jdk";

  private final Map<String, ProjectJdkImpl> myCachedProjectJdks = new HashMap<>();

  // constructor is public because it is accessed from Upsource
  public ProjectJdkTableImpl() {
    // support external changes to jdk libraries (Endorsed Standards Override)
    final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        if (events.isEmpty()) {
          return;
        }

        Set<Sdk> affected = null;
        for (VFileEvent event : events) {
          affected = addAffectedJavaSdk(event, affected);
        }
        if (affected != null) {
          for (Sdk sdk : affected) {
            ((SdkType)sdk.getSdkType()).setupSdkPaths(sdk);
          }
        }
      }

      private @Nullable Set<Sdk> addAffectedJavaSdk(@NotNull VFileEvent event, @Nullable Set<Sdk> affected) {
        CharSequence fileName = null;
        if (event instanceof VFileCreateEvent) {
          if (((VFileCreateEvent)event).isDirectory()) {
            return affected;
          }
          fileName = ((VFileCreateEvent)event).getChildName();
        }
        else {
          VirtualFile file = event.getFile();
          if (file != null && file.isValid()) {
            if (file.isDirectory()) {
              return affected;
            }
            fileName = file.getNameSequence();
          }
        }

        if (fileName == null) {
          fileName = VfsUtil.extractFileName(event.getPath());
        }
        if (fileName != null) {
          // avoid calling getFileType() because it will try to detect file type from content for unknown/text file types
          // consider only archive files that may contain libraries
          if (!ArchiveFileType.INSTANCE.equals(FileTypeManager.getInstance().getFileTypeByFileName(fileName))) {
            return affected;
          }
        }

        for (Sdk sdk : mySdks) {
          if (sdk.getSdkType() instanceof JavaSdkType && (affected == null || !affected.contains(sdk))) {
            String homePath = sdk.getHomePath();
            String eventPath = event.getPath();
            if (!Strings.isEmpty(homePath) && FileUtil.isAncestor(homePath, eventPath, true)) {
              if (affected == null) {
                affected = new HashSet<>();
              }
              affected.add(sdk);
            }
          }
        }
        return affected;
      }
    });

    SdkType.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull SdkType extension, @NotNull PluginDescriptor pluginDescriptor) {
        loadSdkType(extension);
      }

      @Override
      public void extensionRemoved(@NotNull SdkType extension, @NotNull PluginDescriptor pluginDescriptor) {
        forgetSdkType(extension);
      }
    }, null);
  }

  private void loadSdkType(@NotNull SdkType newSdkType) {
    for (Sdk sdk : mySdks) {
      SdkTypeId sdkType = sdk.getSdkType();
      if (sdkType instanceof UnknownSdkType && sdkType.getName().equals(newSdkType.getName()) && sdk instanceof ProjectJdkImpl) {
        WriteAction.run(() -> {
          Element additionalData = saveSdkAdditionalData(sdk);
          ((ProjectJdkImpl)sdk).changeType(newSdkType, additionalData);
        });
      }
    }
  }

  private void forgetSdkType(@NotNull SdkType extension) {
    Set<Sdk> toRemove = new HashSet<>();
    for (Sdk sdk : mySdks) {
      SdkTypeId sdkType = sdk.getSdkType();
      if (sdkType == extension) {
        if (sdk instanceof ProjectJdkImpl) {
          Element additionalDataElement = saveSdkAdditionalData(sdk);
          ((ProjectJdkImpl)sdk).changeType(UnknownSdkType.getInstance(sdkType.getName()), additionalDataElement);
        }
        else {
          //sdk was dynamically added by a plugin so we can only remove it
          toRemove.add(sdk);
        }
      }
    }
    for (Sdk sdk : toRemove) {
      removeJdk(sdk);
    }
  }

  @Nullable
  private static Element saveSdkAdditionalData(Sdk sdk) {
    SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
    Element additionalDataElement;
    if (additionalData != null) {
      additionalDataElement = new Element(ProjectJdkImpl.ELEMENT_ADDITIONAL);
      sdk.getSdkType().saveAdditionalData(additionalData, additionalDataElement);
    }
    else {
      additionalDataElement = null;
    }
    return additionalDataElement;
  }

  @Override
  public File @NotNull [] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("jdk.table")};
  }

  @Override
  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("sdk.table.settings");
  }

  @Override
  @Nullable
  public Sdk findJdk(@NotNull String name) {
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
  public Sdk findJdk(@NotNull String name, @NotNull String type) {
    Sdk projectJdk = findJdk(name);
    if (projectJdk != null) {
      return projectJdk;
    }
    final String uniqueName = type + "." + name;
    projectJdk = myCachedProjectJdks.get(uniqueName);
    if (projectJdk != null) return projectJdk;

    @NonNls final String jdkPrefix = "jdk.";
    final String jdkPath = System.getProperty(jdkPrefix + name);
    if (jdkPath == null) return null;

    final SdkType sdkType = SdkType.findByName(type);
    if (sdkType != null && sdkType.isValidSdkHome(jdkPath)) {
      ProjectJdkImpl projectJdkImpl = new ProjectJdkImpl(name, sdkType);
      projectJdkImpl.setHomePath(jdkPath);
      sdkType.setupSdkPaths(projectJdkImpl);
      myCachedProjectJdks.put(uniqueName, projectJdkImpl);
      return projectJdkImpl;
    }
    return null;
  }

  @Override
  public Sdk @NotNull [] getAllJdks() {
    return mySdks.toArray(new Sdk[0]);
  }

  @NotNull
  @Override
  public List<Sdk> getSdksOfType(@NotNull final SdkTypeId type) {
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
  public void addJdk(@NotNull Sdk jdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (mySdks.contains(jdk)) {
      throw new IllegalStateException("Sdk " + jdk + " is already registered.");
    }
    mySdks.add(jdk);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkAdded(jdk);
  }

  @Override
  public void removeJdk(@NotNull Sdk jdk) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkRemoved(jdk);
    mySdks.remove(jdk);
    if (jdk instanceof Disposable) {
      Disposer.dispose((Disposable)jdk);
    }
  }

  @Override
  public void updateJdk(@NotNull Sdk originalJdk, @NotNull Sdk modifiedJdk) {
    final String previousName = originalJdk.getName();
    final String newName = modifiedJdk.getName();

    ((ProjectJdkImpl)modifiedJdk).copyTo((ProjectJdkImpl)originalJdk);

    if (!previousName.equals(newName)) {
      // fire changes because after renaming JDK its name may match the associated jdk name of modules/project
      ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkNameChanged(originalJdk, previousName);
    }
  }

  @Override
  @NotNull
  public SdkTypeId getDefaultSdkType() {
    return UnknownSdkType.getInstance("");
  }

  @Override
  @NotNull
  public SdkTypeId getSdkTypeByName(@NotNull String sdkTypeName) {
    return findSdkTypeByName(sdkTypeName);
  }

  @NotNull
  private static SdkTypeId findSdkTypeByName(@NotNull String sdkTypeName) {
    final SdkType[] allSdkTypes = SdkType.getAllTypes();
    for (final SdkType type : allSdkTypes) {
      if (type.getName().equals(sdkTypeName)) {
        return type;
      }
    }
    return UnknownSdkType.getInstance(sdkTypeName);
  }

  @NotNull
  @Override
  public Sdk createSdk(@NotNull final String name, @NotNull final SdkTypeId sdkType) {
    return new ProjectJdkImpl(name, sdkType);
  }

  @Override
  public void loadState(@NotNull Element element) {
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
      if (jdk instanceof ProjectJdkImpl) {
        Element e = new Element(ELEMENT_JDK);
        ((ProjectJdkImpl)jdk).writeExternal(e);
        element.addContent(e);
      }
    }
    return element;
  }
}
