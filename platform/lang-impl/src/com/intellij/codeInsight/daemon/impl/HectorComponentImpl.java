// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurableProvider;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.*;

class HectorComponentImpl extends JPanel implements HectorComponent {
  private static final Logger LOG = Logger.getInstance(HectorComponent.class);

  private WeakReference<JBPopup> myHectorRef;
  private final ArrayList<HectorComponentPanel> myAdditionalPanels;
  private final Map<Language, JSlider> mySliders;
  private final PsiFile myFile;

  HectorComponentImpl(@NotNull PsiFile file) {
    super(new GridBagLayout());
    setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));
    myFile = file;
    mySliders = new HashMap<>();

    Project project = myFile.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    VirtualFile virtualFile = myFile.getContainingFile().getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    boolean notInLibrary =
      !fileIndex.isInLibrary(virtualFile) || fileIndex.isInContent(virtualFile);
    FileViewProvider viewProvider = myFile.getViewProvider();
    List<Language> languages = new ArrayList<>(viewProvider.getLanguages());
    languages.sort(PsiUtilBase.LANGUAGE_COMPARATOR);
    for (Language language : languages) {
      @SuppressWarnings("UseOfObsoleteCollectionType") Hashtable<Integer, JComponent> sliderLabels = new Hashtable<>();
      sliderLabels.put(1, new JLabel(EditorBundle.message("hector.none.slider.label"), AllIcons.Ide.HectorOff, SwingConstants.LEFT));
      sliderLabels.put(2, new JLabel(EditorBundle.message("hector.syntax.slider.label"), AllIcons.Ide.HectorSyntax, SwingConstants.LEFT));
      if (notInLibrary) {
        sliderLabels.put(3, new JLabel(EditorBundle.message("hector.inspections.slider.label"), AllIcons.Ide.HectorOn, SwingConstants.LEFT));
      }

      JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, notInLibrary ? 3 : 2, 1);
      slider.setLabelTable(sliderLabels);
      UIUtil.setSliderIsFilled(slider, true);
      slider.setPaintLabels(true);
      slider.setSnapToTicks(true);
      slider.addChangeListener(e -> {
        int value = slider.getValue();
        for (Enumeration<Integer> enumeration = sliderLabels.keys(); enumeration.hasMoreElements(); ) {
          Integer key = enumeration.nextElement();
          sliderLabels.get(key).setForeground(key.intValue() <= value ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground());
        }
      });

      PsiFile psiRoot = viewProvider.getPsi(language);
      assert psiRoot != null : "No root in " + viewProvider + " for " + language;
      slider.setValue(getValue(HighlightingLevelManager.getInstance(project).shouldHighlight(psiRoot),
                               HighlightingLevelManager.getInstance(project).shouldInspect(psiRoot)));
      mySliders.put(language, slider);
    }

    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.NONE, JBUI.insetsLeft(5), 0, 0);

    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(EditorBundle.message("hector.highlighting.level.title"), false));
    boolean addLabel = mySliders.size() > 1;
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

    HyperlinkLabel configurator = new HyperlinkLabel(DaemonBundle.message("popup.action.configure.inspections"));
    gc.insets.right = 5;
    gc.insets.bottom = 10;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.EAST;
    add(configurator, gc);
    configurator.addHyperlinkListener(e -> {
      JBPopup hector = getOldHector();
      if (hector != null) {
        hector.cancel();
      }
      if (!DaemonCodeAnalyzer.getInstance(myFile.getProject()).isHighlightingAvailable(myFile)) return;
      Project project1 = myFile.getProject();
      ShowSettingsUtil.getInstance().editConfigurable(project1, ConfigurableExtensionPointUtil
        .createProjectConfigurableForProvider(project1, ErrorsConfigurableProvider.class));
    });

    gc.anchor = GridBagConstraints.WEST;
    gc.weightx = 1.0;
    gc.insets.right = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    myAdditionalPanels = new ArrayList<>();
    for (HectorComponentPanelsProvider provider : HectorComponentPanelsProvider.EP_NAME.getExtensions(project)) {
      HectorComponentPanel componentPanel = provider.createConfigurable(file);
      if (componentPanel != null) {
        myAdditionalPanels.add(componentPanel);
        add(componentPanel.createComponent(), gc);
        componentPanel.reset();
      }
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension preferredSize = super.getPreferredSize();
    int width = JBUIScale.scale(300);
    if (preferredSize.width < width){
      preferredSize.width = width;
    }
    return preferredSize;
  }

  private void layoutHorizontal(JPanel panel) {
    for (JSlider slider : mySliders.values()) {
      slider.setOrientation(SwingConstants.HORIZONTAL);
      slider.setPreferredSize(JBUI.size(200, 40));
      panel.add(slider, new GridBagConstraints(0, 1, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                               JBUI.insets(5, 0), 0, 0));
    }
  }

  private void layoutVertical(JPanel panel) {
    for (Language language : mySliders.keySet()) {
      JSlider slider = mySliders.get(language);
      JPanel borderPanel = new JPanel(new BorderLayout());
      slider.setPreferredSize(JBUI.size(100));
      borderPanel.add(new JLabel(language.getID()), BorderLayout.NORTH);
      borderPanel.add(slider, BorderLayout.CENTER);
      panel.add(borderPanel, new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.VERTICAL,
                                                    JBUI.insets(0, 5), 0, 0));
    }
  }

  @Override
  public void showComponent(@NotNull Component component,
                            @NotNull Function1<? super Dimension, ? extends Point> offset) {
    showComponent(new RelativePoint(component, offset.invoke(getPreferredSize())));
  }

  @Override
  public void showComponent(@NotNull RelativePoint point) {
    JBPopup hector = JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)
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
    Disposer.register(myFile.getProject(), () -> {
      JBPopup oldHector = getOldHector();
      if (oldHector != null && !oldHector.isDisposed()) {
        Disposer.dispose(oldHector);
      }
      Disposer.dispose(hector);
    });
    JBPopup oldHector = getOldHector();
    if (oldHector != null){
      oldHector.cancel();
    } else {
      myHectorRef = new WeakReference<>(hector);
      UIEventLogger.HectorPopupDisplayed.log(myFile.getProject());
      hector.show(point);
    }
  }

  @Nullable
  private JBPopup getOldHector(){
    if (myHectorRef == null) return null;
    JBPopup hector = myHectorRef.get();
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
    }
  }

  private void forceDaemonRestart() {
    FileViewProvider viewProvider = myFile.getViewProvider();
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
    InjectedLanguageManager.getInstance(myFile.getProject()).dropFileCaches(myFile);
    DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(myFile.getProject());
    analyzer.restart();
  }

  private boolean isModified() {
    FileViewProvider viewProvider = myFile.getViewProvider();
    for (Language language : mySliders.keySet()) {
      JSlider slider = mySliders.get(language);
      PsiFile root = viewProvider.getPsi(language);
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
