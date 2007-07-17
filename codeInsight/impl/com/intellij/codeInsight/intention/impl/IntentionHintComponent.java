package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.RowIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author max
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 */
public class IntentionHintComponent extends JPanel implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.IntentionHintComponent.ListPopupRunnable");

  private static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");
  private static final Icon ourIntentionOffIcon = IconLoader.getIcon("/actions/intentionOffBulb.png");
  private static final Icon ourQuickFixOffIcon = IconLoader.getIcon("/actions/quickfixOffBulb.png");
  private static final Icon ourArrowIcon = IconLoader.getIcon("/general/arrowDown.png");
  private static final Border INACTIVE_BORDER = null;
  private static final Insets INACTIVE_MARGIN = new Insets(0, 0, 0, 0);
  private static final Insets ACTIVE_MARGIN = new Insets(0, 0, 0, 0);

  private final Project myProject;
  private final Editor myEditor;

  private static final Alarm myAlarm = new Alarm();

  private final RowIcon myHighlightedIcon;
  private final JButton myButton;

  private final Icon mySmartTagIcon;

  private static final int DELAY = 500;
  private final MyComponentHint myComponentHint;
  private static final Color BACKGROUND_COLOR = new Color(255, 255, 255, 0);
  private boolean myPopupShown = false;
  private ListPopup myPopup;
  private static final TObjectHashingStrategy<IntentionActionWithTextCaching> ACTION_TEXT_AND_CLASS_EQUALS = new TObjectHashingStrategy<IntentionActionWithTextCaching>() {
    public int computeHashCode(final IntentionActionWithTextCaching object) {
      return object.getText().hashCode();
    }

    public boolean equals(final IntentionActionWithTextCaching o1, final IntentionActionWithTextCaching o2) {
      return o1.getAction().getClass() == o2.getAction().getClass() && o1.getText().equals(o2.getText());
    }
  };
  private final PsiFile myFile;

  private class IntentionListStep implements ListPopupStep<IntentionActionWithTextCaching>, SpeedSearchFilter<IntentionActionWithTextCaching> {
    private final Set<IntentionActionWithTextCaching> myCachedIntentions = new THashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
    private final Set<IntentionActionWithTextCaching> myCachedErrorFixes = new THashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
    private final Set<IntentionActionWithTextCaching> myCachedInspectionFixes = new THashSet<IntentionActionWithTextCaching>(ACTION_TEXT_AND_CLASS_EQUALS);
    private final IntentionManagerSettings mySettings;

    private IntentionListStep(List<HighlightInfo.IntentionActionDescriptor> intentions, List<HighlightInfo.IntentionActionDescriptor> quickFixes,
                              final List<HighlightInfo.IntentionActionDescriptor> inspectionFixes) {
      mySettings = IntentionManagerSettings.getInstance();
      updateActions(intentions, quickFixes, inspectionFixes);
    }

    //true if nothing changed
    private boolean updateActions(final List<HighlightInfo.IntentionActionDescriptor> intentions, final List<HighlightInfo.IntentionActionDescriptor> errorFixes, final List<HighlightInfo.IntentionActionDescriptor> inspectionFixes) {
      boolean result = wrapActionsTo(errorFixes, myCachedErrorFixes);
      result &= wrapActionsTo(inspectionFixes, myCachedInspectionFixes);
      result &= wrapActionsTo(intentions, myCachedIntentions);
      return result;
    }

    private boolean wrapActionsTo(final List<HighlightInfo.IntentionActionDescriptor> descriptors,
                               final Set<IntentionActionWithTextCaching> cachedActions) {
      boolean result = true;
      for (HighlightInfo.IntentionActionDescriptor descriptor : descriptors) {
        IntentionAction action = descriptor.getAction();
        IntentionActionWithTextCaching cachedAction = new IntentionActionWithTextCaching(action, descriptor.getDisplayName());
        result &= !cachedActions.add(cachedAction);
        final int caretOffset = myEditor.getCaretModel().getOffset();
        final int fileOffset = caretOffset > 0 && caretOffset == myFile.getTextLength() ? caretOffset - 1 : caretOffset;
        PsiElement element = InjectedLanguageUtil.findElementAt(myFile, fileOffset);
        final List<IntentionAction> options;
        if (element != null && (options = descriptor.getOptions(element)) != null) {
          for (IntentionAction option : options) {
            boolean isErrorFix = myCachedErrorFixes.contains(new IntentionActionWithTextCaching(option, option.getText()));
            if (isErrorFix) {
              cachedAction.addErrorFix(option);
            }
            boolean isInspectionFix = myCachedInspectionFixes.contains(new IntentionActionWithTextCaching(option, option.getText()));
            if (isInspectionFix) {
              cachedAction.addInspectionFix(option);
            }
            else {
              cachedAction.addIntention(option);
            }
          }
        }
      }
      result &= removeInvalidActions(cachedActions);
      return result;
    }

    private boolean removeInvalidActions(final Collection<IntentionActionWithTextCaching> cachedActions) {
      boolean result = true;
      Iterator<IntentionActionWithTextCaching> iterator = cachedActions.iterator();
      while (iterator.hasNext()) {
        IntentionActionWithTextCaching cachedAction = iterator.next();
        IntentionAction action = cachedAction.getAction();
        if (!myFile.isValid() || !action.isAvailable(myProject, myEditor, myFile)) {
          iterator.remove();
          result = false;
        }
      }
      return result;
    }

    public String getTitle() {
      return null;
    }

    public boolean isSelectable(final IntentionActionWithTextCaching action) {
      return true;
    }

    public PopupStep onChosen(final IntentionActionWithTextCaching action, final boolean finalChoice) {
      if (finalChoice && !(action.getAction() instanceof EmptyIntentionAction)) {
        applyAction(action);
        return PopupStep.FINAL_CHOICE;
      }

      if (hasSubstep(action)) {
        return getSubStep(action);
      }

      return FINAL_CHOICE;
    }

    private PopupStep getSubStep(final IntentionActionWithTextCaching action) {
      final ArrayList<HighlightInfo.IntentionActionDescriptor> intentions = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
      for (final IntentionAction optionIntention : action.getOptionIntentions()) {
        intentions.add(new HighlightInfo.IntentionActionDescriptor(optionIntention, null));
      }
      final ArrayList<HighlightInfo.IntentionActionDescriptor> errorFixes = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
      for (final IntentionAction optionFix : action.getOptionErrorFixes()) {
        errorFixes.add(new HighlightInfo.IntentionActionDescriptor(optionFix, null));
      }
      final ArrayList<HighlightInfo.IntentionActionDescriptor> inspectionFixes = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
      for (final IntentionAction optionFix : action.getOptionInspectionFixes()) {
        inspectionFixes.add(new HighlightInfo.IntentionActionDescriptor(optionFix, null));
      }

      return new IntentionListStep(intentions, errorFixes, inspectionFixes){
        public String getTitle() {
          return XmlStringUtil.escapeString(action.getToolName());
        }
      };
    }

    public boolean hasSubstep(final IntentionActionWithTextCaching action) {
      return action.getOptionIntentions().size() + action.getOptionErrorFixes().size() > 0;
    }

    @NotNull
    public List<IntentionActionWithTextCaching> getValues() {
      List<IntentionActionWithTextCaching> result = new ArrayList<IntentionActionWithTextCaching>(myCachedErrorFixes);
      result.addAll(myCachedInspectionFixes);
      result.addAll(myCachedIntentions);
      Collections.sort(result, new Comparator<IntentionActionWithTextCaching>() {
        public int compare(final IntentionActionWithTextCaching o1, final IntentionActionWithTextCaching o2) {
          int weight1 = myCachedErrorFixes.contains(o1) ? 2 : myCachedInspectionFixes.contains(o1) ? 1 : 0;
          int weight2 = myCachedErrorFixes.contains(o2) ? 2 : myCachedInspectionFixes.contains(o2) ? 1 : 0;
          if (weight1 != weight2) {
            return weight2 - weight1;
          }
          return Comparing.compare(o1.getText(), o2.getText());
        }
      });
      return result;
    }

    @NotNull
    public String getTextFor(final IntentionActionWithTextCaching action) {
      return action.getText();
    }

    public Icon getIconFor(final IntentionActionWithTextCaching value) {
      final IntentionAction action = value.getAction();

      if (mySettings.isShowLightBulb(action)) {
        if (myCachedErrorFixes.contains(value)) {
          return ourQuickFixIcon;
        }
        else {
          return ourIntentionIcon;
        }
      }
      else {
        if (myCachedErrorFixes.contains(value)) {
          return ourQuickFixOffIcon;
        }
        else {
          return ourIntentionOffIcon;
        }
      }
    }

    public void canceled() {
      if (myPopup.getListStep() == this) {
        // Root canceled. Create new popup. This one cannot be reused.
        myPopup = JBPopupFactory.getInstance().createListPopup(this);
      }
    }

    public int getDefaultOptionIndex() { return 0; }
    public ListSeparator getSeparatorAbove(final IntentionActionWithTextCaching value) {
      List<IntentionActionWithTextCaching> values = getValues();
      int index = values.indexOf(value);
      if (index == 0) return null;
      IntentionActionWithTextCaching prev = values.get(index - 1);

      if (myCachedErrorFixes.contains(value) != myCachedErrorFixes.contains(prev)
        || myCachedInspectionFixes.contains(value) != myCachedInspectionFixes.contains(prev)
        || myCachedIntentions.contains(value) != myCachedIntentions.contains(prev)) {
        return new ListSeparator();
      }
      return null;
    }
    public boolean isMnemonicsNavigationEnabled() { return false; }
    public MnemonicNavigationFilter<IntentionActionWithTextCaching> getMnemonicNavigationFilter() { return null; }
    public boolean isSpeedSearchEnabled() { return true; }
    public boolean isAutoSelectionEnabled() { return false; }
    public SpeedSearchFilter<IntentionActionWithTextCaching> getSpeedSearchFilter() { return this; }

    //speed search filter
    public boolean canBeHidden(final IntentionActionWithTextCaching value) { return true;}
    public String getIndexedString(final IntentionActionWithTextCaching value) { return getTextFor(value);}
  }

  private static class IntentionActionWithTextCaching implements Comparable<IntentionActionWithTextCaching> {
    private final List<IntentionAction> myOptionIntentions;
    private final List<IntentionAction> myOptionErrorFixes;
    private final String myText;
    private final IntentionAction myAction;
    private final String myDisplayName;
    private final List<IntentionAction> myOptionInspectionFixes;

    public IntentionActionWithTextCaching(IntentionAction action, String displayName) {
      myOptionIntentions = new ArrayList<IntentionAction>();
      myOptionErrorFixes = new ArrayList<IntentionAction>();
      myOptionInspectionFixes = new ArrayList<IntentionAction>();
      myText = action.getText();
      // needed for checking errors in user written actions
      //noinspection ConstantConditions
      LOG.assertTrue(myText != null, "action "+action.getClass()+" text returned null");
      myAction = action;
      myDisplayName = displayName;
    }

    String getText() {
      return myText;
    }

    public void addIntention(final IntentionAction action) {
        myOptionIntentions.add(action);
    }
    public void addErrorFix(final IntentionAction action) {
      myOptionErrorFixes.add(action);
    }
    public void addInspectionFix(final IntentionAction action) {
      myOptionInspectionFixes.add(action);
    }

    public IntentionAction getAction() {
      return myAction;
    }

    public List<IntentionAction> getOptionIntentions() {
      return myOptionIntentions;
    }

    public List<IntentionAction> getOptionErrorFixes() {
      return myOptionErrorFixes;
    }

    public List<IntentionAction> getOptionInspectionFixes() {
      return myOptionInspectionFixes;
    }

    public String getToolName() {
      return myDisplayName;
    }

    public String toString() {
      return getText();
    }

    public int compareTo(final IntentionActionWithTextCaching other) {
      if (myAction instanceof Comparable) {
        return ((Comparable)myAction).compareTo(other.getAction());
      }
      else if (other.getAction() instanceof Comparable) {
        return ((Comparable)other.getAction()).compareTo(myAction);
      }
      return Comparing.compare(getText(), other.getText());
    }
  }

  public static IntentionHintComponent showIntentionHint(Project project, final PsiFile file, Editor editor,
                                                         List<HighlightInfo.IntentionActionDescriptor> intentions,
                                                         List<HighlightInfo.IntentionActionDescriptor> errorFixes,
                                                         final List<HighlightInfo.IntentionActionDescriptor> inspectionFixes, boolean showExpanded) {
    final IntentionHintComponent component = new IntentionHintComponent(project, file, editor, intentions, errorFixes, inspectionFixes);

    if (showExpanded) {
      component.showIntentionHintImpl(false);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          component.showPopup();
        }
      });
    }
    else {
      component.showIntentionHintImpl(true);
    }
    Disposer.register(project, component);

    return component;
  }

  public void dispose() {
    closePopup();
    myComponentHint.hide();
    super.hide();
  }

  //true if success
  public boolean updateActions(List<HighlightInfo.IntentionActionDescriptor> intentions, List<HighlightInfo.IntentionActionDescriptor> errorFixes,
                               final List<HighlightInfo.IntentionActionDescriptor> inspectionFixes) {
    if (myPopup.getContent() == null) {
      // already disposed
      return false;
    }
    IntentionListStep step = (IntentionListStep)myPopup.getListStep();
    if (!step.updateActions(intentions, errorFixes, inspectionFixes)) {
      if (!myPopupShown) {
        myPopup = JBPopupFactory.getInstance().createListPopup(step);
        return true;
      }
      return false;
    }

    return true;
  }

  private void showIntentionHintImpl(final boolean delay) {
    final int offset = myEditor.getCaretModel().getOffset();

    myComponentHint.setShouldDelay(delay);

    HintManager.getInstance().showQuestionHint(myEditor,
                                 getHintPosition(myEditor, offset),
                                 offset,
                                 offset,
                                 myComponentHint,
                                 new QuestionAction() {
                                   public boolean execute() {
                                     showPopup();
                                     return true;
                                   }
                                 });
  }

  private static Point getHintPosition(Editor editor, int offset) {
    final LogicalPosition pos = editor.offsetToLogicalPosition(offset);
    int line = pos.line;


    final Point position = editor.logicalPositionToXY(new LogicalPosition(line, 0));
    final int yShift = (ourIntentionIcon.getIconHeight() - editor.getLineHeight() - 1) / 2 - 1;

    LOG.assertTrue(editor.getComponent().isDisplayable());
    Point location = SwingUtilities.convertPoint(editor.getContentComponent(),
                                                 new Point(editor.getScrollingModel().getVisibleArea().x, position.y + yShift),
                                                 editor.getComponent().getRootPane().getLayeredPane());

    return new Point(location.x, location.y);
  }

  private IntentionHintComponent(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor, @NotNull List<HighlightInfo.IntentionActionDescriptor> intentions,
                                 @NotNull List<HighlightInfo.IntentionActionDescriptor> errorFixes,
                                 final List<HighlightInfo.IntentionActionDescriptor> inspectionFixes) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myFile = file;
    myProject = project;
    myEditor = editor;

    setLayout(new BorderLayout());
    setOpaque(false);

    boolean showFix = false;
    for (final HighlightInfo.IntentionActionDescriptor pairs : errorFixes) {
      IntentionAction fix = pairs.getAction();
      if (IntentionManagerSettings.getInstance().isShowLightBulb(fix)) {
        showFix = true;
        break;
      }
    }
    mySmartTagIcon = showFix ? ourQuickFixIcon : ourIntentionIcon;

    myHighlightedIcon = new RowIcon(2);
    myHighlightedIcon.setIcon(mySmartTagIcon, 0);
    myHighlightedIcon.setIcon(ourArrowIcon, 1);

    myButton = new JButton(mySmartTagIcon);
    myButton.setFocusable(false);
    myButton.setMargin(INACTIVE_MARGIN);
    myButton.setBorderPainted(false);
    myButton.setContentAreaFilled(false);

    add(myButton, BorderLayout.CENTER);
    setBorder(INACTIVE_BORDER);

    myButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showPopup();
      }
    });

    myButton.addMouseListener(new MouseAdapter() {
      public void mouseEntered(MouseEvent e) {
        onMouseEnter();
      }

      public void mouseExited(MouseEvent e) {
        onMouseExit();
      }
    });

    myComponentHint = new MyComponentHint(this);
    myPopup = JBPopupFactory.getInstance().createListPopup(new IntentionListStep(intentions, errorFixes, inspectionFixes));
  }

  public void hide() {
    Disposer.dispose(this);
  }

  private void onMouseExit() {
    Window ancestor = SwingUtilities.getWindowAncestor(myPopup.getContent());
    if (ancestor == null) {
      myButton.setBackground(BACKGROUND_COLOR);
      myButton.setIcon(mySmartTagIcon);
      setBorder(INACTIVE_BORDER);
      myButton.setMargin(INACTIVE_MARGIN);
      updateComponentHintSize();
    }
  }

  private void onMouseEnter() {
    myButton.setBackground(HintUtil.QUESTION_COLOR);
    myButton.setIcon(myHighlightedIcon);
    setBorder(BorderFactory.createLineBorder(Color.black));
    myButton.setMargin(ACTIVE_MARGIN);
    updateComponentHintSize();

    String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    if (acceleratorsText.length() > 0) {
      myButton.setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", acceleratorsText));
    }
  }

  private void updateComponentHintSize() {
    Component component = myComponentHint.getComponent();
    component.setSize(getPreferredSize().width, getHeight());
  }

  public void closePopup() {
    if (myPopupShown) {
      myPopup.cancel();
      myPopupShown = false;
    }
  }

  private void showPopup() {
    if (isShowing()) {
      myPopup.show(RelativePoint.getSouthWestOf(this));
    }
    else {
      myPopup.showInBestPositionFor(myEditor);
    }

    myPopupShown = true;
  }

  private class MyComponentHint extends LightweightHint {
    private boolean myVisible = false;
    private boolean myShouldDelay;

    public MyComponentHint(JComponent component) {
      super(component);
    }

    public void show(final JComponent parentComponent, final int x, final int y, final JComponent focusBackComponent) {
      myVisible = true;
      if (myShouldDelay) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          public void run() {
            showImpl(parentComponent, x, y, focusBackComponent);
          }
        }, DELAY);
      }
      else {
        showImpl(parentComponent, x, y, focusBackComponent);
      }
    }

    private void showImpl(JComponent parentComponent, int x, int y, JComponent focusBackComponent) {
      if (!parentComponent.isShowing()) return;
      super.show(parentComponent, x, y, focusBackComponent);
    }

    public void hide() {
      super.hide();
      myVisible = false;
      myAlarm.cancelAllRequests();
    }

    public boolean isVisible() {
      return myVisible || super.isVisible();
    }

    public void setShouldDelay(boolean shouldDelay) {
      myShouldDelay = shouldDelay;
    }
  }

  private void applyAction(final IntentionActionWithTextCaching cachedAction) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        HintManager.getInstance().hideAllHints();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            PsiDocumentManager.getInstance(myProject).commitAllDocuments();
            final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
            final IntentionAction action = cachedAction.getAction();
            if (file == null || !action.isAvailable(myProject, myEditor, file)) {
              return;
            }
            Runnable runnable = new Runnable() {
              public void run() {
                try {
                  action.invoke(myProject, myEditor, file);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
                DaemonCodeAnalyzer.getInstance(myProject).updateVisibleHighlighters(myEditor);
              }
            };

            if (action.startInWriteAction()) {
              final Runnable _runnable = runnable;
              runnable = new Runnable() {
                public void run() {
                  ApplicationManager.getApplication().runWriteAction(_runnable);
                }
              };
            }

            CommandProcessor.getInstance().executeCommand(myProject, runnable, cachedAction.getText(), null);
          }
        });
      }
    });
  }

  public static class EnableDisableIntentionAction implements IntentionAction{
    private String myActionFamilyName;
    private IntentionManagerSettings mySettings = IntentionManagerSettings.getInstance();

    public EnableDisableIntentionAction(IntentionAction action) {
      myActionFamilyName = action.getFamilyName();
      // needed for checking errors in user written actions
      //noinspection ConstantConditions
      LOG.assertTrue(myActionFamilyName != null, "action "+action.getClass()+" family returned null");
    }

    @NotNull
    public String getText() {
      return mySettings.isEnabled(myActionFamilyName) ?
             CodeInsightBundle.message("disable.intention.action", myActionFamilyName) :
             CodeInsightBundle.message("enable.intention.action", myActionFamilyName);
    }

    @NotNull
    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      mySettings.setEnabled(myActionFamilyName, !mySettings.isEnabled(myActionFamilyName));
    }

    public boolean startInWriteAction() {
      return false;
    }
  }
}
