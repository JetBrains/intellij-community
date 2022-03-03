// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IntentionActionMetaData extends BeforeAfterActionMetaData {
  private final @NotNull IntentionAction myAction;
  public final String @NotNull [] myCategory;
  private String myDirName;
  private static final @NonNls String INTENTION_DESCRIPTION_FOLDER = "intentionDescriptions";

  public IntentionActionMetaData(@NotNull IntentionAction action,
                                 @Nullable ClassLoader loader,
                                 String @NotNull [] category,
                                 @NotNull String descriptionDirectoryName) {
    super(loader, descriptionDirectoryName);

    myAction = action;
    myCategory = category;
  }

  @Override
  public String toString() {
    return getFamily();
  }

  public @Nullable PluginId getPluginId() {
    if (myLoader instanceof PluginAwareClassLoader) {
      return ((PluginAwareClassLoader)myLoader).getPluginId();
    }
    return null;
  }

  public @NotNull @IntentionFamilyName String getFamily() {
    return myAction.getFamilyName();
  }

  public @NotNull IntentionAction getAction() {
    return myAction;
  }

  @Override
  protected String getResourceLocation(String resourceName) {
    if (myDirName == null) {
      String dirName = myDescriptionDirectoryName;
      if (myLoader != null && myLoader.getResource(getResourceLocationStatic(dirName, resourceName)) == null) {
        dirName = getFamily();

        if (myLoader.getResource(getResourceLocationStatic(dirName, resourceName)) == null) {
          PluginId pluginId = getPluginId();
          String errorMessage = "Intention Description Dir URL is null: " + getFamily() + "; "
                                + myDescriptionDirectoryName + "; while looking for " + resourceName;
          if (pluginId != null) {
            throw new PluginException(errorMessage, pluginId);
          }
          else {
            throw new RuntimeException(errorMessage);
          }
        }
      }
      myDirName = dirName;
    }

    return getResourceLocationStatic(myDirName, resourceName);
  }

  private static @NotNull String getResourceLocationStatic(String dirName, String resourceName) {
    return INTENTION_DESCRIPTION_FOLDER + "/" + dirName + "/" + resourceName;
  }

  public String getDescriptionDirectoryName() {
    return myDescriptionDirectoryName;
  }
}