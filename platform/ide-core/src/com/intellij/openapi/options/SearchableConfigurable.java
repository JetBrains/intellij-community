// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * SearchableConfigurable instances would be instantiated on buildSearchableOptions step during Installer's build to index of all available options. 
 * {@link com.intellij.ide.ui.search.TraverseUIStarter}
 * 
 * @see SearchableOptionContributor
 */
public interface SearchableConfigurable extends ConfigurableWithId {
  /**
   * @param option setting search query
   * @return an action to perform when this configurable is opened when a search filter query is entered by the user in setting dialog.
   * This action, for example, can select something in a tree or a list embedded in this setting page that matches the query. 
   */
  default @Nullable Runnable enableSearch(String option) {
    return null;
  }

  /**
   * When building an index of searchable options, it's important to know a class which caused the creation of a configurable.
   * It often happens that the configurable is created based on a provider from an arbitrary extension point.
   * In such a case, the provider's class should be returned from this method.
   * <br/>
   * When the configurable is based on several providers consider extending {@link com.intellij.openapi.options.CompositeConfigurable}.
   * <br/>
   * Keep in mind that this method can be expensive as it can load previously unloaded class.
   *
   * @return a class which is a cause of the creation of this configurable
   */
  default @NotNull Class<?> getOriginalClass() {
    return this.getClass();
  }

  interface Parent extends SearchableConfigurable, Composite {
    default boolean hasOwnContent() {
      return false;
    }

    abstract class Abstract implements Parent {
      private Configurable[] myKids;

      @Override
      public JComponent createComponent() {
        return null;
      }

      @Override
      public boolean isModified() {
        return false;
      }

      @Override
      public void apply() throws ConfigurationException {
      }

      @Override
      public void disposeUIResources() {
        myKids = null;
      }

      @Override
      public final Configurable @NotNull [] getConfigurables() {
        if (myKids != null) return myKids;
        myKids = buildConfigurables();
        return myKids;
      }

      protected abstract Configurable[] buildConfigurables();
    }
  }

  @FunctionalInterface
  interface Merged {
    List<Configurable> getMergedConfigurables();
  }

  /**
   * Intended to use some search utility methods with any configurable.
   */
  class Delegate implements SearchableConfigurable {
    private final Configurable myConfigurable;

    public Delegate(@NotNull Configurable configurable) {
      myConfigurable = configurable;
    }

    @Override
    public @NotNull String getId() {
      return myConfigurable instanceof SearchableConfigurable
             ? ((SearchableConfigurable)myConfigurable).getId()
             : myConfigurable.getClass().getName();
    }

    @Override
    public @Nullable Runnable enableSearch(String option) {
      return myConfigurable instanceof SearchableConfigurable
             ? ((SearchableConfigurable)myConfigurable).enableSearch(option)
             : null;
    }

    @Override
    public @Nls String getDisplayName() {
      return myConfigurable.getDisplayName();
    }

    @Override
    public @Nullable String getHelpTopic() {
      return myConfigurable.getHelpTopic();
    }

    @Override
    public @Nullable JComponent createComponent() {
      return myConfigurable.createComponent();
    }

    @Override
    public boolean isModified() {
      return myConfigurable.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
      myConfigurable.apply();
    }

    @Override
    public void reset() {
      myConfigurable.reset();
    }

    @Override
    public void disposeUIResources() {
      myConfigurable.disposeUIResources();
    }
  }
}
