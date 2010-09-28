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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
* @author nik
*/
public class FrameworkSupportNode extends CheckedTreeNode {
  private final FrameworkSupportProvider myProvider;
  private final FrameworkSupportNode myParentNode;
  private final FrameworkSupportConfigurable myConfigurable;
  private final List<FrameworkSupportNode> myChildren = new ArrayList<FrameworkSupportNode>();
  private LibraryCompositionSettings myLibraryCompositionSettings;
  private final Computable<String> myBaseDirForLibrariesGetter;
  private LibraryOptionsPanel myLibraryCompositionOptionsPanel;

  public FrameworkSupportNode(final FrameworkSupportProvider provider, final FrameworkSupportNode parentNode, final FrameworkSupportModelImpl model,
                             Computable<String> baseDirForLibrariesGetter, Disposable parentDisposable) {
    super(provider);
    myBaseDirForLibrariesGetter = baseDirForLibrariesGetter;
    setChecked(false);
    myProvider = provider;
    myParentNode = parentNode;
    model.registerComponent(provider, this);
    myConfigurable = provider.createConfigurable(model);
    Disposer.register(parentDisposable, myConfigurable);
    if (parentNode != null) {
      parentNode.add(this);
      parentNode.myChildren.add(this);
    }

    setConfigurableComponentEnabled(false);
  }

  public List<FrameworkSupportNode> getChildren() {
    return myChildren;
  }

  @Nullable
  public LibraryOptionsPanel getLibraryCompositionOptionsPanel(LibrariesContainer librariesContainer) {
    final LibraryCompositionSettings libraryCompositionSettings = getLibraryCompositionSettings();
    if (myLibraryCompositionOptionsPanel == null || !myLibraryCompositionOptionsPanel.getSettings().equals(libraryCompositionSettings)) {
      if (libraryCompositionSettings != null) {
        myLibraryCompositionOptionsPanel = new LibraryOptionsPanel(libraryCompositionSettings, librariesContainer, true);
      }
      else {
        myLibraryCompositionOptionsPanel = null;
      }
    }
    return myLibraryCompositionOptionsPanel;
  }

  public void setConfigurableComponentEnabled(final boolean enable) {
    JComponent component = getConfigurable().getComponent();
    if (component != null) {
      UIUtil.setEnabled(component, enable, true);
    }
  }

  public FrameworkSupportProvider getProvider() {
    return myProvider;
  }

  public FrameworkSupportNode getParentNode() {
    return myParentNode;
  }

  public FrameworkSupportConfigurable getConfigurable() {
    return myConfigurable;
  }

  private boolean isObsolete(@NotNull LibraryCompositionSettings settings) {
    final LibraryInfo[] libraries = getLibraries();
    return !settings.getBaseDirectoryForDownloadedFiles().equals(myBaseDirForLibrariesGetter.compute())
           || !Comparing.equal(settings.getLibraryInfos(), libraries);
  }

  public LibraryInfo[] getLibraries() {
    final FrameworkVersion version = myConfigurable.getSelectedVersion();
    return version != null ? version.getLibraries() : LibraryInfo.EMPTY_ARRAY;
  }

  @Nullable
  public LibraryCompositionSettings getLibraryCompositionSettings() {
    if (myLibraryCompositionSettings == null || isObsolete(myLibraryCompositionSettings)) {
      final LibraryInfo[] libraries = getLibraries();
      if (libraries.length != 0) {
        myLibraryCompositionSettings = new LibraryCompositionSettings(libraries, myConfigurable.getSelectedVersion().getLibraryName(), myBaseDirForLibrariesGetter.compute()
        );
        Disposer.register(myConfigurable, myLibraryCompositionSettings);
      }
      else {
        myLibraryCompositionSettings = null;
      }
    }
    return myLibraryCompositionSettings;
  }

  public static void sortByTitle(List<FrameworkSupportNode> nodes) {
    if (nodes.isEmpty()) return;

    Collections.sort(nodes, new Comparator<FrameworkSupportNode>() {
      public int compare(final FrameworkSupportNode o1, final FrameworkSupportNode o2) {
        return getTitleWithoutMnemonic(o1.getProvider()).compareTo(getTitleWithoutMnemonic(o2.getProvider()));
      }
    });
    for (FrameworkSupportNode node : nodes) {
      node.sortChildren();
    }
  }

  public String getTitle() {
    return getTitleWithoutMnemonic(myProvider);
  }

  private static String getTitleWithoutMnemonic(final FrameworkSupportProvider provider) {
    return GuiUtils.getTextWithoutMnemonicEscaping(provider.getTitle());
  }

  private void sortChildren() {
    sortByTitle(myChildren);
  }
}
