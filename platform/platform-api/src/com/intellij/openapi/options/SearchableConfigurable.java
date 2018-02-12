/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.options;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * SearchableConfigurable instances would be instantiated on buildSearchableOptions step during Installer's build to index of all available options. 
 * {@link #com.intellij.ide.ui.search.TraverseUIStarter}
 * 
 * @see SearchableOptionContributor
 */
public interface SearchableConfigurable extends Configurable {

  /**
   * Unique configurable id.
   * Note this id should be THE SAME as the one specified in XML.
   * @see ConfigurableEP#id
   */
  @NotNull
  @NonNls
  String getId();

  /**
   * @param option setting search query
   * @return an action to perform when this configurable is opened when a search filter query is entered by the user in setting dialog.
   * This action, for example, can select something in a tree or a list embedded in this setting page that matches the query. 
   */
  default @Nullable Runnable enableSearch(String option) {
    return null;
  }

  interface Parent extends SearchableConfigurable, Composite {
    default boolean hasOwnContent() {
      return false;
    }

    /**
     * @deprecated use {@link ConfigurableProvider#canCreateConfigurable()} instead
     *             to specify configurables which should not be visible
     *             (a provider usually does not instantiate a configurable and related classes)
     */
    @Deprecated
    default boolean isVisible() {
      return true;
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

      @NotNull
      @Override
      public final Configurable[] getConfigurables() {
        if (myKids != null) return myKids;
        myKids = buildConfigurables();
        return myKids;
      }

      protected abstract Configurable[] buildConfigurables();
    }
  }

  /**
   * Intended to use some search utility methods with any configurable.
   *
   * @author Sergey.Malenkov
   */
  class Delegate implements SearchableConfigurable {
    private final Configurable myConfigurable;

    public Delegate(@NotNull Configurable configurable) {
      myConfigurable = configurable;
    }

    @NotNull
    @Override
    public String getId() {
      return (myConfigurable instanceof SearchableConfigurable)
             ? ((SearchableConfigurable)myConfigurable).getId()
             : myConfigurable.getClass().getName();
    }

    @Nullable
    @Override
    public Runnable enableSearch(String option) {
      return (myConfigurable instanceof SearchableConfigurable)
             ? ((SearchableConfigurable)myConfigurable).enableSearch(option)
             : null;
    }

    @Nls
    @Override
    public String getDisplayName() {
      return myConfigurable.getDisplayName();
    }

    @Nullable
    @Override
    public String getHelpTopic() {
      return myConfigurable.getHelpTopic();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
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
