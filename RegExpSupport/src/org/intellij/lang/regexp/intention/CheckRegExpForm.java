// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp.intention;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.IncrementalFindAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.SmartList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.regexp.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class CheckRegExpForm {
  private static final Logger LOG = Logger.getInstance(CheckRegExpForm.class);

  public static final Key<Boolean> CHECK_REG_EXP_EDITOR = Key.create("CHECK_REG_EXP_EDITOR");

  private static final Key<List<RegExpMatch>> LAST_MATCHES = Key.create("REG_EXP_LAST_MATCHES");

  private static final String LAST_EDITED_REGEXP = "last.edited.regexp";

  private final EditorTextField mySampleText;

  private final JPanel myRootPanel;
  private final JBLabel myRegExpIcon = new JBLabel();
  private final JBLabel mySampleIcon = new JBLabel();

  private final SmartList<RangeHighlighter> highlighters = new SmartList<>();

  public CheckRegExpForm(@NotNull PsiFile regexpFile) {
    final Project project = regexpFile.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(regexpFile);

    final Language language = regexpFile.getLanguage();
    final LanguageFileType fileType;
    if (language instanceof RegExpLanguage) {
      fileType = RegExpLanguage.INSTANCE.getAssociatedFileType();
    }
    else {
      // for correct syntax highlighting
      fileType = new RegExpFileType(language);
    }
    final EditorTextField myRegExp = new EditorTextField(document, project, fileType, false, false) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        editor.putUserData(CHECK_REG_EXP_EDITOR, Boolean.TRUE);
        editor.putUserData(IncrementalFindAction.SEARCH_DISABLED, Boolean.TRUE);
        editor.setEmbeddedIntoDialogWrapper(true);
        return editor;
      }

      @Override
      protected void updateBorder(@NotNull EditorEx editor) {
        setupBorder(editor);
      }
    };
    setupIcon(myRegExp, myRegExpIcon);

    final String sampleText = PropertiesComponent.getInstance(project).getValue(LAST_EDITED_REGEXP, "Sample Text");
    mySampleText = new EditorTextField(sampleText, project, PlainTextFileType.INSTANCE) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        editor.putUserData(IncrementalFindAction.SEARCH_DISABLED, Boolean.TRUE);
        editor.setEmbeddedIntoDialogWrapper(true);
        return editor;
      }

      @Override
      protected void updateBorder(@NotNull EditorEx editor) {
        setupBorder(editor);
      }
    };

    setupIcon(mySampleText, mySampleIcon);
    mySampleText.setOneLineMode(false);
    final int preferredWidth = Math.max(JBUIScale.scale(250), myRegExp.getPreferredSize().width);
    myRegExp.setPreferredWidth(preferredWidth);
    mySampleText.setPreferredWidth(preferredWidth);

    myRootPanel = new JPanel(new GridBagLayout()) {
      Disposable disposable;
      Alarm updater;

      @Override
      public void addNotify() {
        super.addNotify();
        disposable = Disposer.newDisposable();

        IdeFocusManager.getGlobalInstance().requestFocus(mySampleText, true);

        registerFocusShortcut(myRegExp, "shift TAB", mySampleText);
        registerFocusShortcut(myRegExp, "TAB", mySampleText);
        registerFocusShortcut(mySampleText, "shift TAB", myRegExp);
        registerFocusShortcut(mySampleText, "TAB", myRegExp);

        updater = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable);
        final DocumentListener documentListener = new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent e) {
            update();
          }
        };
        myRegExp.addDocumentListener(documentListener);
        mySampleText.addDocumentListener(documentListener);

        update();
        mySampleText.selectAll();
      }

      private void registerFocusShortcut(JComponent source, String shortcut, EditorTextField target) {
        final AnAction action = new AnAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            IdeFocusManager.findInstance().requestFocus(target.getFocusTarget(), true);
          }
        };
        action.registerCustomShortcutSet(CustomShortcutSet.fromString(shortcut), source);
      }

      private void update() {
        updater.cancelAllRequests();
        if (!updater.isDisposed()) {
          updater.addRequest(() -> {
            final RegExpMatchResult result = isMatchingText(regexpFile, myRegExp.getText(), mySampleText.getText());
            ApplicationManager.getApplication().invokeLater(() -> reportResult(result, regexpFile), ModalityState.any(), __ -> updater.isDisposed());
          }, 0);
        }
      }

      @Override
      public void removeNotify() {
        super.removeNotify();
        Disposer.dispose(disposable);
        PropertiesComponent.getInstance(project).setValue(LAST_EDITED_REGEXP, mySampleText.getText());
      }
    };
    myRootPanel.setBorder(JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP));

    final GridBagConstraints c = new GridBagConstraints();
    c.insets = JBUI.insets(UIUtil.DEFAULT_VGAP / 2, UIUtil.DEFAULT_HGAP / 2);
    c.gridx = 0;
    c.gridy = 0;
    myRootPanel.add(createLabel(RegExpBundle.message("label.regexp"), myRegExp), c);
    c.gridx = 1;
    myRootPanel.add(myRegExp, c);
    c.gridx = 0;
    c.gridy++;
    myRootPanel.add(createLabel(RegExpBundle.message("label.sample"), mySampleText), c);
    c.gridx = 1;
    myRootPanel.add(mySampleText, c);
  }

  private static JLabel createLabel(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component) {
    final JLabel label = new JLabel(UIUtil.removeMnemonic(labelText));
    final int index = UIUtil.getDisplayMnemonicIndex(labelText);
    if (index != -1) {
      label.setDisplayedMnemonic(labelText.charAt(index + 1));
      label.setDisplayedMnemonicIndex(index);
    }
    label.setLabelFor(component);
    return label;
  }

  private static void setupIcon(@NotNull EditorTextField field, @NotNull JComponent icon) {
    field.addSettingsProvider(editor -> {
      icon.setBorder(JBUI.Borders.emptyLeft(2));
      final JScrollPane scrollPane = editor.getScrollPane();
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
      final JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
      verticalScrollBar.setBackground(editor.getBackgroundColor());
      verticalScrollBar.add(JBScrollBar.LEADING, icon);
      verticalScrollBar.setOpaque(true);
    });
  }

  void reportResult(RegExpMatchResult result, @NotNull PsiFile regexpFile) {
    switch (result) {
      case NO_MATCH:
        setIconAndTooltip(mySampleIcon, AllIcons.General.BalloonError, RegExpBundle.message("tooltip.no.match"));
        setIconAndTooltip(myRegExpIcon, null, null);
        break;
      case MATCHES:
        setIconAndTooltip(mySampleIcon, AllIcons.General.InspectionsOK, RegExpBundle.message("tooltip.matches"));
        setIconAndTooltip(myRegExpIcon, null, null);
        break;
      case FOUND:
        final List<RegExpMatch> matches = getMatches(regexpFile);
        final Editor editor = mySampleText.getEditor();
        if (editor != null) {
          ApplicationManager.getApplication().invokeLater(() -> {
            final HighlightManager highlightManager = HighlightManager.getInstance(regexpFile.getProject());
            for (RangeHighlighter highlighter : highlighters) {
              highlightManager.removeSegmentHighlighter(editor, highlighter);
            }
            for (RegExpMatch match : matches) {
              final int start = match.start(0);
              final int end = match.end(0);
              highlightManager.addRangeHighlight(editor, start, end, RegExpHighlighter.MATCHED_GROUPS, true, highlighters);
            }
          });
        }
        if (matches.size() > 1) {
          setIconAndTooltip(mySampleIcon, AllIcons.General.InspectionsOK, RegExpBundle.message("tooltip.found.multiple", matches.size()));
        }
        else {
          setIconAndTooltip(mySampleIcon, AllIcons.General.InspectionsOK, RegExpBundle.message("tooltip.found"));
        }
        setIconAndTooltip(myRegExpIcon, null, null);
        break;
      case INCOMPLETE:
        setIconAndTooltip(mySampleIcon, AllIcons.General.BalloonWarning, RegExpBundle.message("tooltip.more.input.expected"));
        setIconAndTooltip(myRegExpIcon, null, null);
        break;
      case BAD_REGEXP:
        setIconAndTooltip(mySampleIcon, null, null);
        setIconAndTooltip(myRegExpIcon, AllIcons.General.BalloonError, RegExpBundle.message("tooltip.bad.pattern"));
        break;
      case TIMEOUT:
        setIconAndTooltip(mySampleIcon, null, null);
        setIconAndTooltip(myRegExpIcon, AllIcons.General.BalloonWarning, RegExpBundle.message("tooltip.pattern.is.too.complex"));
        break;
    }
  }

  private static void setIconAndTooltip(JBLabel label, Icon icon, @NlsContexts.Tooltip String tooltip) {
    label.setIcon(icon);
    label.setToolTipText(tooltip);
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return mySampleText;
  }

  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  private List<RegExpMatch> getMatches(PsiFile regexpFile) {
    return regexpFile.getUserData(LAST_MATCHES);
  }

  @TestOnly
  public static boolean isMatchingTextTest(@NotNull PsiFile regexpFile, @NotNull String sampleText) {
    return isMatchingText(regexpFile, regexpFile.getText(), sampleText) == RegExpMatchResult.MATCHES;
  }

  static RegExpMatchResult isMatchingText(@NotNull final PsiFile regexpFile, String regexpText, @NotNull String sampleText) {
    final Language regexpFileLanguage = regexpFile.getLanguage();
    final RegExpMatcherProvider matcherProvider = RegExpMatcherProvider.EP.forLanguage(regexpFileLanguage);
    if (matcherProvider != null) {
      final RegExpMatchResult result = ReadAction.compute(() -> {
        final PsiLanguageInjectionHost host = InjectedLanguageUtil.findInjectionHost(regexpFile);
        if (host != null) {
          return matcherProvider.matches(regexpText, regexpFile, host, sampleText, 1000L);
        }
        return null;
      });
      if (result != null) {
        return result;
      }
    }

    final Integer patternFlags = ReadAction.compute(() -> {
      final PsiLanguageInjectionHost host = InjectedLanguageUtil.findInjectionHost(regexpFile);
      int flags = 0;
      if (host != null) {
        for (RegExpModifierProvider provider : RegExpModifierProvider.EP.allForLanguage(host.getLanguage())) {
          flags = provider.getFlags(host, regexpFile);
          if (flags > 0) break;
        }
      }
      return flags;
    });

    try {
      //noinspection MagicConstant
      final Matcher matcher = Pattern.compile(regexpText, patternFlags).matcher(StringUtil.newBombedCharSequence(sampleText, 1000));
      if (matcher.matches()) {
        return RegExpMatchResult.MATCHES;
      }
      else if (matcher.hitEnd()) {
        return RegExpMatchResult.INCOMPLETE;
      }
      else if (matcher.find()) {
        final SmartList<RegExpMatch> matches = new SmartList<>();
        do {
          final RegExpMatch match = new RegExpMatch();
          final int count = matcher.groupCount();
          for (int i = 0; i <= count; i++) {
            match.add(matcher.start(i), matcher.end(i));
          }
          matches.add(match);
        } while (matcher.find());
        regexpFile.putUserData(LAST_MATCHES, matches);
        return RegExpMatchResult.FOUND;
      }
      else {
        return RegExpMatchResult.NO_MATCH;
      }
    }
    catch (ProcessCanceledException ignore) {
      return RegExpMatchResult.TIMEOUT;
    }
    catch (Exception e) {
      LOG.warn(e);
    }

    return RegExpMatchResult.BAD_REGEXP;
  }
}