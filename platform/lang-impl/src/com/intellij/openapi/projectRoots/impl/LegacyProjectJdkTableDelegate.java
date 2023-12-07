// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.Comparing;
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
import com.intellij.workspaceModel.ide.legacyBridge.sdk.SdkTableImplementationDelegate;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

import static com.intellij.openapi.projectRoots.ProjectJdkTable.JDK_TABLE_TOPIC;


@State(name = "ProjectJdkTable",
  storages = @Storage(value = "jdk.table.xml", roamingType = RoamingType.DISABLED, useSaveThreshold = ThreeState.NO))
public class LegacyProjectJdkTableDelegate implements SdkTableImplementationDelegate, PersistentStateComponent<Element> {
  private final List<Sdk> mySdks = new ArrayList<>();

  @NonNls
  private static final String ELEMENT_JDK = "jdk";

  // constructor is public because it is accessed from Upsource
  private LegacyProjectJdkTableDelegate() {
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
  }

  @Nullable
  @Override
  public Sdk findSdkByName(@NotNull String name) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, len = mySdks.size(); i < len; ++i) { // avoid foreach,  it instantiates ArrayList$Itr, this traversal happens very often
      final Sdk jdk = mySdks.get(i);
      if (Comparing.strEqual(name, jdk.getName())) {
        return jdk;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<Sdk> getAllSdks() {
    return mySdks;
  }

  @Override
  public void addNewSdk(@NotNull Sdk jdk) {
    if (mySdks.contains(jdk)) {
      throw new IllegalStateException("Sdk " + jdk + " is already registered.");
    }
    mySdks.add(jdk);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkAdded(jdk);
  }

  @Override
  public void removeSdk(@NotNull Sdk sdk) {
    mySdks.remove(sdk);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkRemoved(sdk);
  }

  @Override
  public void updateSdk(@NotNull Sdk originalSdk, @NotNull Sdk modifiedSdk) {
    String previousName = originalSdk.getName();
    String newName = modifiedSdk.getName();

    ((ProjectJdkImpl)modifiedSdk).copyTo((ProjectJdkImpl)originalSdk);

    if (!previousName.equals(newName)) {
      // fire changes because after renaming JDK its name may match the associated jdk name of modules/project
      ApplicationManager.getApplication().getMessageBus().syncPublisher(JDK_TABLE_TOPIC).jdkNameChanged(originalSdk, previousName);
    }
  }

  @NotNull
  @Override
  public Sdk createSdk(@NotNull String name, @NotNull SdkTypeId type, @Nullable String homePath) {
    return new ProjectJdkImpl(name, type, homePath != null ? homePath : "", null);
  }

  @Override
  public void loadState(@NotNull Element element) {
    mySdks.clear();

    for (Element child : element.getChildren(ELEMENT_JDK)) {
      ProjectJdkImpl jdk = new ProjectJdkImpl(null, null);
      jdk.readExternal(child, (sdkTypeName) -> {
        return Arrays.stream(SdkType.getAllTypes())
          .filter(type -> type.getName().equals(sdkTypeName))
          .findFirst()
          .orElseGet(() -> UnknownSdkType.getInstance(sdkTypeName));
      });
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

  @Override
  @TestOnly
  public void saveOnDisk() {
    IComponentStore store = ServiceKt.getStateStore(ApplicationManager.getApplication());
    store.saveComponent(this);
  }
}