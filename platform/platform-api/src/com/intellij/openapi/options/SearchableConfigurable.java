// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.ide.ui.search.SearchableOptionContributor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * SearchableConfigurable instances would be instantiated on buildSearchableOptions step during Installer's build to index of all available options. 
 * {@link com.intellij.ide.ui.search.TraverseUIStarter}
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
  @Nullable
  default Runnable enableSearch(String option) {
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
  @NotNull
  default Class<?> getOriginalClass() {
    return this.getClass();
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
      return myConfigurable instanceof SearchableConfigurable
             ? ((SearchableConfigurable)myConfigurable).getId()
             : myConfigurable.getClass().getName();
    }

    @Nullable
    @Override
    public Runnable enableSearch(String option) {
      return myConfigurable instanceof SearchableConfigurable
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
