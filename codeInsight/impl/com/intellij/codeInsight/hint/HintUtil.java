package com.intellij.codeInsight.hint;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SideBorder2;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredText;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.ui.UIUtil;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

public class HintUtil {
  public static final Color INFORMATION_COLOR = new Color(253, 254, 226);
  public static final Color QUESTION_COLOR = new Color(181, 208, 251);
  private static final Color ERROR_COLOR = new Color(255, 220, 220);

  private static final Icon INFORMATION_ICON = null;
  private static final Icon QUESTION_ICON = IconLoader.getIcon("/actions/help.png");
  private static final Icon ERROR_ICON = null;

  public static final Color QUESTION_UNDERSCORE_COLOR = Color.black;

  private HintUtil() {
  }

  public static JLabel createInformationLabel(String text) {
    JLabel label = new HintLabel();
    label.setText(text);
    label.setIcon(INFORMATION_ICON);
    label.setBorder(
      BorderFactory.createCompoundBorder(
        new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1),
        BorderFactory.createEmptyBorder(2, 2, 2, 2)
      )
    );
    label.setForeground(Color.black);
    label.setFont(getBoldFont());
    label.setBackground(INFORMATION_COLOR);
    label.setOpaque(true);

    return label;
  }

  public static JComponent createInformationLabel(SimpleColoredText text) {
    SimpleColoredComponent  highlighted = new SimpleColoredComponent ();

    highlighted.setIcon(INFORMATION_ICON);
    highlighted.setBackground(INFORMATION_COLOR);
    highlighted.setForeground(Color.black);
    highlighted.setFont(getBoldFont());
    text.appendToComponent(highlighted);


    Box box = Box.createHorizontalBox();
    box.setBorder(
      new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1)
    );
    box.setForeground(Color.black);
    box.setBackground(INFORMATION_COLOR);
    box.add(highlighted);
    box.setOpaque(true);

    return box;
  }

  public static JLabel createQuestionLabel(String text) {
    JLabel label = new HintLabel();
    label.setText(text);
    label.setIcon(QUESTION_ICON);
//    label.setBorder(BorderFactory.createLineBorder(Color.black));
    label.setBorder(
      BorderFactory.createCompoundBorder(
        new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1),
        BorderFactory.createEmptyBorder(2, 2, 2, 2)
      )
    );
    label.setForeground(Color.black);
    label.setFont(getBoldFont());
    label.setBackground(QUESTION_COLOR);
    label.setOpaque(true);
    return label;
  }

  public static ImplementationTextSelectioner getImplementationTextSelectioner(final Language language) {
    ImplementationTextSelectioner implementationTextSelectioner = ourTextSelectionsMap.get(language);
    if (implementationTextSelectioner == null) implementationTextSelectioner = ourTextSelectionsMap.get(StdLanguages.JAVA);
    return implementationTextSelectioner;
  }

  public static void registerImplementationTextSelectioner(final Language language, final ImplementationTextSelectioner selectioner) {
    ourTextSelectionsMap.put(language, selectioner);
  }

  public interface ImplementationTextSelectioner {
    int getTextStartOffset(@NotNull PsiElement element);
    int getTextEndOffset(@NotNull PsiElement element);
  }

  // TODO: Move to Language OpeanAPI
  private static final Map<Language,ImplementationTextSelectioner> ourTextSelectionsMap
    = new HashMap<Language, ImplementationTextSelectioner>(2);

  static class DefaultImplementationTextSelectioner implements ImplementationTextSelectioner {

    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.HintUtil.DefaultImplementationTextSelectioner");

    public int getTextStartOffset(@NotNull final PsiElement parent) {
      PsiElement element = parent;

      if (element instanceof PsiDocCommentOwner) {
        PsiDocComment comment = ((PsiDocCommentOwner)element).getDocComment();
        if (comment != null) {
          element = comment.getNextSibling();
          while (element instanceof PsiWhiteSpace) {
            element = element.getNextSibling();
          }
        }
      }

      if (element != null) {
        return element.getTextRange().getStartOffset();
      } else {
        LOG.assertTrue(false, "Element should not be null: " + parent.getText());
        return parent.getTextRange().getStartOffset();
      }


    }

    public int getTextEndOffset(PsiElement element) {
      return element.getTextRange().getEndOffset();
    }
  }

  static {
    ourTextSelectionsMap.put(StdLanguages.JAVA, new DefaultImplementationTextSelectioner());
  }

  public static JLabel createErrorLabel(String text) {
    JLabel label = new HintLabel();
    label.setText(text);
    label.setIcon(ERROR_ICON);
//    label.setBorder(BorderFactory.createLineBorder(Color.black));
    label.setBorder(
      BorderFactory.createCompoundBorder(
        new SideBorder2(Color.white, Color.white, Color.gray, Color.gray, 1),
        BorderFactory.createEmptyBorder(2, 2, 2, 2)
      )
    );
    label.setForeground(Color.black);
    label.setFont(getBoldFont());
    label.setBackground(ERROR_COLOR);
    label.setOpaque(true);
    return label;
  }

  private static Font getBoldFont() {
    return UIUtil.getLabelFont().deriveFont(Font.BOLD);
  }

  private static class HintLabel extends JLabel {
    public void setText(String s) {
      if (s == null) {
        super.setText(null);
      }
      else {
        super.setText(" " + s + " ");
      }
    }
  }
}