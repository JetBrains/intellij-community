// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.ide.plugins.PluginNode;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class PluginGroupDescription {

  public abstract @NotNull PluginId getPluginId();

  public abstract @NotNull @NlsSafe String getName();

  public abstract @NotNull @Nls String getCategory();

  public abstract @NotNull @Nls String getDescription();

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PluginGroupDescription that = (PluginGroupDescription)o;
    return getPluginId().equals(that.getPluginId());
  }

  @Override
  public final int hashCode() {
    return getPluginId().hashCode();
  }

  /**
   * TODO regenerate after {@link PluginGroups#parseString(String)} removal.
   */
  @Override
  public final String toString() {
    return StringUtil.join(new String[]{getCategory(), getDescription(), getPluginId().getIdString()}, ":");
  }

  public static @NotNull PluginGroupDescription vim() {
    return new Impl("IdeaVIM",
                    "IdeaVim",
                    "Editor",
                    "Emulates Vim editor");
  }

  public static @NotNull PluginGroupDescription aws() {
    return new Impl("aws.toolkit",
                    "AWS Toolkit",
                    "Cloud Support",
                    "Create, test, and debug serverless applications built using the AWS Serverless Application Model");
  }

  public static @NotNull PluginGroupDescription r() {
    return new Impl("R4Intellij",
                    "R",
                    "Custom Languages",
                    "R language support");
  }

  public static @NotNull PluginGroupDescription scala() {
    return new Impl("org.intellij.scala",
                    "Scala",
                    "Custom Languages",
                    "Scala language support");
  }

  public static @NotNull PluginGroupDescription bigDataTools() {
    return new Impl("com.intellij.bigdatatools",
                    "Big Data Tools",
                    "Tools Integration",
                    "Zeppelin notebooks and Spark applications support");
  }

  public static @NotNull PluginGroupDescription featuresTrainer() {
    return new Impl("training",
                    "IDE Features Trainer",
                    "Code tools",
                    "Learn basic shortcuts and essential features interactively");
  }

  public static @NotNull PluginGroupDescription teamCity() {
    return new Impl("JetBrains TeamCity Plugin",
                    "TeamCity Integration",
                    "Tools Integration",
                    "Integration with JetBrains TeamCity - innovative solution for continuous integration and build management");
  }

  public static @NotNull PluginGroupDescription liveEdit() {
    return new Impl("com.intellij.plugins.html.instantEditing",
                    "Live Edit Tool",
                    "Web Development",
                    "Live WYSIWYG Editing of HTML, CSS, and JavaScript");
  }

  public static @NotNull PluginGroupDescription ideolog() {
    return new Impl("com.intellij.ideolog",
                    "Ideolog",
                    "Languages",
                    "Interactive viewer for '.log' files");
  }

  public static @NotNull PluginGroupDescription create(@NotNull @NonNls String idString,
                                                       @NotNull @NlsSafe String name,
                                                       @NotNull @Nls String category,
                                                       @NotNull @Nls String description) {
    return new Impl(idString, name, category, description);
  }

  private static final class Impl extends PluginGroupDescription {

    private final @NotNull PluginId myPluginId;
    private final @NotNull @NlsSafe String myName;
    private final @NotNull @Nls String myCategory;
    private final @NotNull @Nls String myDescription;

    private Impl(@NotNull @NonNls String idString,
                 @NotNull @NlsSafe String name,
                 @NotNull @Nls String category,
                 @NotNull @Nls String description) {
      myPluginId = PluginId.getId(idString);
      myName = name;
      myCategory = category;
      myDescription = description;
    }

    @Override
    public @NotNull PluginId getPluginId() {
      return myPluginId;
    }

    @Override
    public @NotNull @NlsSafe String getName() {
      return myName;
    }

    @Override
    public @NotNull @Nls String getCategory() {
      return myCategory;
    }

    @Override
    public @NotNull @Nls String getDescription() {
      return myDescription;
    }
  }

  static final class CloudDelegate extends PluginGroupDescription {

    private final @NotNull PluginGroupDescription myDelegate;
    private @Nullable PluginNode myPluginNode;

    CloudDelegate(@NotNull PluginId pluginId,
                  @NotNull PluginGroupDescription delegate) {
      assert pluginId.equals(delegate.getPluginId());
      myDelegate = delegate;
    }

    @Nullable PluginNode getPluginNode() {
      return myPluginNode;
    }

    void setPluginNode(@Nullable PluginNode pluginNode) {
      assert pluginNode == null || getPluginId().equals(pluginNode.getPluginId());
      myPluginNode = pluginNode;
    }

    @Override
    public @NotNull PluginId getPluginId() {
      return myDelegate.getPluginId();
    }

    @Override
    public @NotNull @NlsSafe String getName() {
      return getDefaultIfEmpty(PluginNode::getName,
                               myDelegate::getName);
    }

    @Override
    public @NotNull @Nls String getCategory() {
      return getDefaultIfEmpty(PluginNode::getCategory,
                               myDelegate::getCategory);
    }

    @Override
    public @NotNull @Nls String getDescription() {
      return getDefaultIfEmpty(PluginNode::getDescription,
                               myDelegate::getDescription);
    }

    private @NotNull @Nls String getDefaultIfEmpty(@NotNull Function<@NotNull PluginNode, @NotNull @Nls String> byProperty,
                                                   @NotNull Supplier<@NotNull @Nls String> defaultValue) {
      return StringUtil.defaultIfEmpty(myPluginNode != null ? byProperty.apply(myPluginNode) : null,
                                       defaultValue.get());
    }
  }
}