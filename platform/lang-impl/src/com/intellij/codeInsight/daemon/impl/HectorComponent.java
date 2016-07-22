/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: Jun 27, 2005
 */
public class HectorComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.editor.impl.HectorComponent");

  private WeakReference<JBPopup> myHectorRef;
  private final ArrayList<HectorComponentPanel> myAdditionalPanels;
  private final Map<Language, JSlider> mySliders;
  private final PsiFile myFile;

  private final String myTitle = EditorBundle.message("hector.highlighting.level.title");

  public HectorComponent(@NotNull PsiFile file) {
    super(new GridBagLayout());
    setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));
    myFile = file;
    mySliders = new HashMap<>();

    final Project project = myFile.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile virtualFile = myFile.getContainingFile().getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    final boolean notInLibrary =
      !fileIndex.isInLibrarySource(virtualFile) && !fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInContent(virtualFile);
    final FileViewProvider viewProvider = myFile.getViewProvider();
    List<Language> languages = new ArrayList<>(viewProvider.getLanguages());
    Collections.sort(languages, PsiUtilBase.LANGUAGE_COMPARATOR);
    for (Language language : languages) {
      @SuppressWarnings("UseOfObsoleteCollectionType")
      final Hashtable<Integer, JComponent> sliderLabels = new Hashtable<>();
      sliderLabels.put(1, new JLabel(EditorBundle.message("hector.none.slider.label"), AllIcons.Ide.HectorOff, SwingConstants.LEFT));
      sliderLabels.put(2, new JLabel(EditorBundle.message("hector.syntax.slider.label"), AllIcons.Ide.HectorSyntax, SwingConstants.LEFT));
      if (notInLibrary) {
        sliderLabels.put(3, new JLabel(EditorBundle.message("hector.inspections.slider.label"), AllIcons.Ide.HectorOn, SwingConstants.LEFT));
      }

      final JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, notInLibrary ? 3 : 2, 1);
      if (UIUtil.isUnderGTKLookAndFeel()) {
        // default GTK+ slider UI is way too ugly
        slider.putClientProperty("Slider.paintThumbArrowShape", true);
        slider.setUI(new BasicSliderUI(slider));
      }
      slider.setLabelTable(sliderLabels);
      UIUtil.setSliderIsFilled(slider, true);
      slider.setPaintLabels(true);
      slider.setSnapToTicks(true);
      slider.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          int value = slider.getValue();
          for (Enumeration<Integer> enumeration = sliderLabels.keys(); enumeration.hasMoreElements(); ) {
            Integer key = enumeration.nextElement();
            sliderLabels.get(key).setForeground(key.intValue() <= value ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground());
          }
        }
      });

      final PsiFile psiRoot = viewProvider.getPsi(language);
      assert psiRoot != null : "No root in " + viewProvider + " for " + language;
      slider.setValue(getValue(HighlightingLevelManager.getInstance(project).shouldHighlight(psiRoot),
                               HighlightingLevelManager.getInstance(project).shouldInspect(psiRoot)));
      mySliders.put(language, slider);
    }

    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.NONE, new Insets(0, 5, 0, 0), 0, 0);

    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(myTitle, false));
    final boolean addLabel = mySliders.size() > 1;
    if (addLabel) {
      layoutVertical(panel);
    }
    else {
      layoutHorizontal(panel);
    }
    gc.gridx = 0;
    gc.gridy = 0;
    gc.weighty = 1.0;
    gc.fill = GridBagConstraints.BOTH;
    add(panel, gc);

    gc.gridy = GridBagConstraints.RELATIVE;
    gc.weighty = 0;

    final HyperlinkLabel configurator = new HyperlinkLabel("Configure inspections");
    gc.insets.right = 5;
    gc.insets.bottom = 10;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.EAST;
    add(configurator, gc);
    configurator.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        final JBPopup hector = getOldHector();
        if (hector != null) {
          hector.cancel();
        }
        if (!DaemonCodeAnalyzer.getInstance(myFile.getProject()).isHighlightingAvailable(myFile)) return;
        final Project project = myFile.getProject();
        final ErrorsConfigurable errorsConfigurable = ErrorsConfigurable.SERVICE.createConfigurable(project);
        assert errorsConfigurable != null;
        ShowSettingsUtil.getInstance().editConfigurable(project, errorsConfigurable);
      }
    });

    gc.anchor = GridBagConstraints.WEST;
    gc.weightx = 1.0;
    gc.insets.right = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    myAdditionalPanels = new ArrayList<>();
    for (HectorComponentPanelsProvider provider : Extensions.getExtensions(HectorComponentPanelsProvider.EP_NAME, project)) {
      final HectorComponentPanel componentPanel = provider.createConfigurable(file);
      if (componentPanel != null) {
        myAdditionalPanels.add(componentPanel);
        add(componentPanel.createComponent(), gc);
        componentPanel.reset();
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension preferredSize = super.getPreferredSize();
    final int width = 300;
    if (preferredSize.width < width){
      preferredSize.width = width;
    }
    return preferredSize;
  }

  private void layoutHorizontal(final JPanel panel) {
    for (JSlider slider : mySliders.values()) {
      slider.setOrientation(SwingConstants.HORIZONTAL);
      slider.setPreferredSize(JBUI.size(200, 40));
      panel.add(slider, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                               new Insets(5, 0, 5, 0), 0, 0));
    }
  }

  private void layoutVertical(final JPanel panel) {
    for (Language language : mySliders.keySet()) {
      JSlider slider = mySliders.get(language);
      JPanel borderPanel = new JPanel(new BorderLayout());
      slider.setPreferredSize(JBUI.size(100));
      borderPanel.add(new JLabel(language.getID()), BorderLayout.NORTH);
      borderPanel.add(slider, BorderLayout.CENTER);
      panel.add(borderPanel, new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
                                                    new Insets(0, 5, 0, 5), 0, 0));
    }
  }

  public void showComponent(RelativePoint point) {
    final JBPopup hector = JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)
      .setRequestFocus(true)
      .setMovable(true)
      .setCancelCallback(() -> {
        for (HectorComponentPanel additionalPanel : myAdditionalPanels) {
          if (!additionalPanel.canClose()) {
            return Boolean.FALSE;
          }
        }
        onClose();
        return Boolean.TRUE;
      })
      .createPopup();
    Disposer.register(myFile.getProject(), new Disposable() {
      @Override
      public void dispose() {
        final JBPopup oldHector = getOldHector();
        if (oldHector != null && !oldHector.isDisposed()) {
          Disposer.dispose(oldHector);
        }
        Disposer.dispose(hector);
      }
    });
    final JBPopup oldHector = getOldHector();
    if (oldHector != null){
      oldHector.cancel();
    } else {
      myHectorRef = new WeakReference<>(hector);
      hector.show(point);
    }
  }

  @Nullable
  private JBPopup getOldHector(){
    if (myHectorRef == null) return null;
    final JBPopup hector = myHectorRef.get();
    if (hector == null || !hector.isVisible()){
      myHectorRef = null;
      return null;
    }
    return hector;
  }

  private void onClose() {
    if (isModified()) {
      for (HectorComponentPanel panel : myAdditionalPanels) {
        try {
          panel.apply();
        }
        catch (ConfigurationException e) {
          //shouldn't be
        }
      }
      forceDaemonRestart();
      DaemonListeners.getInstance(myFile.getProject()).updateStatusBar();
    }
  }

  private void forceDaemonRestart() {
    final FileViewProvider viewProvider = myFile.getViewProvider();
    for (Language language : mySliders.keySet()) {
      JSlider slider = mySliders.get(language);
      PsiElement root = viewProvider.getPsi(language);
      assert root != null : "No root in " + viewProvider + " for " + language;
      int value = slider.getValue();
      if (value == 1) {
        HighlightLevelUtil.forceRootHighlighting(root, FileHighlightingSetting.SKIP_HIGHLIGHTING);
      }
      else if (value == 2) {
        HighlightLevelUtil.forceRootHighlighting(root, FileHighlightingSetting.SKIP_INSPECTION);
      }
      else {
        HighlightLevelUtil.forceRootHighlighting(root, FileHighlightingSetting.FORCE_HIGHLIGHTING);
      }
    }
    final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(myFile.getProject());
    analyzer.restart();
  }

  private boolean isModified() {
    final FileViewProvider viewProvider = myFile.getViewProvider();
    for (Language language : mySliders.keySet()) {
      JSlider slider = mySliders.get(language);
      final PsiFile root = viewProvider.getPsi(language);
      HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(myFile.getProject());
      if (root != null && getValue(highlightingLevelManager.shouldHighlight(root), highlightingLevelManager.shouldInspect(root)) != slider.getValue()) {
        return true;
      }
    }
    for (HectorComponentPanel panel : myAdditionalPanels) {
      if (panel.isModified()) {
        return true;
      }
    }

    return false;
  }

  private static int getValue(boolean isSyntaxHighlightingEnabled, boolean isInspectionsHighlightingEnabled) {
    if (!isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled) {
      return 1;
    }
    if (isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled) {
      return 2;
    }
    return 3;
  }
}
