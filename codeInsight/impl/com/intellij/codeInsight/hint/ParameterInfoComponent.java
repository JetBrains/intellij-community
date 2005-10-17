
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SplittingUtil;
import com.intellij.ui.StrikeoutLabel;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.Arrays;

class ParameterInfoComponent extends JPanel{
  private Object[] myObjects;
  private boolean[] myEnabledFlags;
  private int myCurrentParameter;
  private XmlTag myCurrentXmlTag;

  private PsiMethod myHighlightedMethod = null;

  private OneElementComponent[] myPanels;

  private static final Color BACKGROUND_COLOR = HintUtil.INFORMATION_COLOR;
  private static final Color FOREGROUND_COLOR = new Color(0, 0, 0);
//  private static final Color DISABLED_BACKGROUND_COLOR = HintUtil.INFORMATION_COLOR;
  private static final Color DISABLED_FOREGROUND_COLOR = new Color(128, 128, 128);
  private static final Color HIGHLIGHTED_BORDER_COLOR = new Color(231, 254, 234);
  private final Font NORMAL_FONT;
  private final Font BOLD_FONT;
  private static final String NO_PARAMETERS_TEXT = CodeInsightBundle.message("parameter.info.no.parameters");
  private static final String NO_ATTRIBUTES_TEXT = CodeInsightBundle.message("xml.tag.info.no.attributes");
  private static final Border BOTTOM_BORDER = new SideBorder(Color.lightGray, SideBorder.BOTTOM);
  private static final Border BACKGROUND_BORDER = BorderFactory.createLineBorder(BACKGROUND_COLOR);

  protected int myWidthLimit;

  public ParameterInfoComponent(Object[] objects, Editor editor) {
    super(new GridBagLayout());

    JComponent editorComponent = editor.getComponent();
    JLayeredPane layeredPane = editorComponent.getRootPane().getLayeredPane();
    myWidthLimit = layeredPane.getWidth();

    NORMAL_FONT = UIUtil.getLabelFont();
    BOLD_FONT = NORMAL_FONT.deriveFont(Font.BOLD);

    myObjects = objects;
    myEnabledFlags = new boolean[myObjects.length];

    this.setLayout(new GridBagLayout());
    this.setBorder(BorderFactory.createCompoundBorder(LineBorder.createGrayLineBorder(), BorderFactory.createEmptyBorder(0, 5, 0, 5)));
    this.setBackground(BACKGROUND_COLOR);

    myPanels = new OneElementComponent[myObjects.length];
    for(int i = 0; i < myObjects.length; i++) {
      myPanels[i] = new OneElementComponent();
      add(myPanels[i], new GridBagConstraints(0,i,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0));

      myEnabledFlags[i] = true;
    }

    myCurrentParameter = -1;
  }

  public void update(){
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    for(int i = 0; i < myObjects.length; i++) {
      final Object o = myObjects[i];
      if (o instanceof CandidateInfo) {
        CandidateInfo candidateInfo = (CandidateInfo) o;
        updateMethod((PsiMethod) candidateInfo.getElement(), candidateInfo.getSubstitutor(), i, settings);
      }
      else if (o instanceof PsiMethod) {
        if (o instanceof PsiAnnotationMethod) {
          updateAnnotationMethod((PsiAnnotationMethod) o, i);
        }
        else {
          updateMethod((PsiMethod) o, null, i, settings);
        }
      }
      else if (o instanceof PsiTypeParameter) {
        updateTypeParameter((PsiTypeParameter)o, i);
      }
      else if (o instanceof XmlElementDescriptor) {
        updateElementDescriptor((XmlElementDescriptor)o, i);
      }
    }
    invalidate();
    validate();
    repaint();
  }

  private void updateTypeParameter(PsiTypeParameter typeParameter, int i) {
    @NonNls StringBuffer buffer = new StringBuffer();
    buffer.append(typeParameter.getName());
    int highlightEndOffset = buffer.length();
    buffer.append(" extends ");
    buffer.append(StringUtil.join(
      Arrays.asList(typeParameter.getSuperTypes()),
      new Function<PsiClassType, String>() {
        public String fun(final PsiClassType t) {
          return t.getPresentableText();
        }
      }, ", "));

    Color background = i == myCurrentParameter ? HIGHLIGHTED_BORDER_COLOR : BACKGROUND_COLOR;
    myPanels[i].setup(buffer.toString(), 0, highlightEndOffset, false, false, false, background);
    myPanels[i].setBorder(i == (myObjects.length - 1) ? BACKGROUND_BORDER : BOTTOM_BORDER);
  }

  private void updateAnnotationMethod(PsiAnnotationMethod method, int i) {
    @NonNls StringBuffer buffer = new StringBuffer();
    int highlightStartOffset;
    int highlightEndOffset;
    buffer.append(method.getReturnType().getPresentableText());
    buffer.append(" ");
    highlightStartOffset = buffer.length();
    buffer.append(method.getName());
    highlightEndOffset = buffer.length();
    buffer.append("()");
    if (method.getDefaultValue() != null) {
      buffer.append(" default ");
      buffer.append(method.getDefaultValue().getText());
    }
    Color background = method.equals(myHighlightedMethod) ? HIGHLIGHTED_BORDER_COLOR : BACKGROUND_COLOR;
    myPanels[i].setup(buffer.toString(), highlightStartOffset, highlightEndOffset, false, method.isDeprecated(), false, background);
    myPanels[i].setBorder(i == (myObjects.length - 1) ? BACKGROUND_BORDER : BOTTOM_BORDER);
  }

  private void updateMethod(PsiMethod method, PsiSubstitutor substitutor, int i, CodeInsightSettings globalSettings) {
    OneElementComponent component = myPanels[i];

    if (!method.isValid()){
      component.setDisabled();
      return;
    }

    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    StringBuffer buffer = new StringBuffer();

    if (globalSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO){
      if (!method.isConstructor()){
        PsiType returnType = method.getReturnType();
        if (substitutor != null) {
          returnType = substitutor.substitute((returnType));
        }
        buffer.append(returnType.getPresentableText());
        buffer.append(" ");
      }
      buffer.append(method.getName());
      buffer.append("(");
    }

    PsiParameter[] parms = method.getParameterList().getParameters();
    int numParams = parms.length;
    if (numParams > 0){
      for(int j = 0; j < numParams; j++) {
        PsiParameter parm = parms[j];

        int startOffset = buffer.length();

        if (parm.isValid()) {
          PsiType paramType = parm.getType();
          if (substitutor != null) {
            paramType = substitutor.substitute(paramType);
          }
          buffer.append(paramType.getPresentableText());
          String name = parm.getName();
          if (name != null){
            buffer.append(" ");
            buffer.append(name);
          }
        }

        int endOffset = buffer.length();

        if (j < numParams - 1){
          buffer.append(", ");
        }

        if (isEnabled(i) && (j == myCurrentParameter || (j == numParams - 1 && parm.isVarArgs() && myCurrentParameter >= numParams))) {
          highlightStartOffset = startOffset;
          highlightEndOffset = endOffset;
        }
      }
    }
    else{
      buffer.append(NO_PARAMETERS_TEXT);
    }

    if (globalSettings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO){
      buffer.append(")");
    }

    Color background = method.equals(myHighlightedMethod) ? HIGHLIGHTED_BORDER_COLOR : BACKGROUND_COLOR;
    component.setup(buffer.toString(), highlightStartOffset, highlightEndOffset, !isEnabled(i), method.isDeprecated(), false, background);
    component.setBorder(i == (myObjects.length - 1) ? BACKGROUND_BORDER : BOTTOM_BORDER);
  }

  public Object[] getObjects() {
    return myObjects;
  }

  public void setEnabled(int index, boolean enabled){
    myEnabledFlags[index] = enabled;
  }

  private boolean isEnabled(int index){
    return myEnabledFlags[index];
  }

  public void setCurrentParameter(int currentParameter) {
    myCurrentParameter = currentParameter;
  }

  private void updateElementDescriptor(XmlElementDescriptor descriptor, int i) {
    final XmlAttributeDescriptor[] attributes = descriptor != null ? descriptor.getAttributesDescriptors() : new XmlAttributeDescriptor[0];

    StringBuffer buffer = new StringBuffer();
    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    if (attributes.length == 0) {
      buffer.append(NO_ATTRIBUTES_TEXT);
    }
    else {
      StringBuffer text1 = new StringBuffer(" ");
      StringBuffer text2 = new StringBuffer(" ");
      StringBuffer text3 = new StringBuffer(" ");

      for (XmlAttributeDescriptor attribute : attributes) {
        if (myCurrentXmlTag != null && myCurrentXmlTag.getAttributeValue(attribute.getName()) != null) {
          if (!(text1.toString().equals(" "))) {
            text1.append(", ");
          }
          text1.append(attribute.getName());
        }
        else if (attribute.isRequired()) {
          if (!(text2.toString().equals(" "))) {
            text2.append(", ");
          }
          text2.append(attribute.getName());
        }
        else {
          if (!(text3.toString().equals(" "))) {
            text3.append(", ");
          }
          text3.append(attribute.getName());
        }
      }

      if (!text1.toString().equals(" ") && !text2.toString().equals(" ")) {
        text1.append(", ");
      }

      if (!text2.toString().equals(" ") && !text3.toString().equals(" ")) {
        text2.append(", ");
      }

      if (!text1.toString().equals(" ") && !text3.toString().equals(" ") && text2.toString().equals(" ")) {
        text1.append(", ");
      }

      buffer.append(text1);
      highlightStartOffset = buffer.length();
      buffer.append(text2);
      highlightEndOffset = buffer.length();
      buffer.append(text3);
    }

    myPanels[i].setup(buffer.toString(), highlightStartOffset, highlightEndOffset, false, false, true, BACKGROUND_COLOR);

    myPanels[i].setBorder(i == (myObjects.length - 1) ? BACKGROUND_BORDER : BOTTOM_BORDER);
  }

  public void setCurrentItem (PsiElement element) {
    if (element == null) {
      myCurrentXmlTag = null;
    }
    else if (element instanceof XmlTag)  myCurrentXmlTag = (XmlTag)element;
  }

  public void setHighlightedMethod(PsiMethod method) {
    myHighlightedMethod = method;
  }

  private class OneElementComponent extends JPanel {
    private OneLineComponent[] myOneLineComponents;

    public OneElementComponent(){
      super(new GridBagLayout());
      myOneLineComponents = new OneLineComponent[0]; //TODO ???
    }

    public void setup(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout, boolean isDisabledBeforeHighlight, Color background) {
      removeAll();

      String[] lines = SplittingUtil.splitText(text, getFontMetrics(BOLD_FONT), myWidthLimit, ',');

      myOneLineComponents = new OneLineComponent[lines.length];

      int lineOffset = 0;

      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];

        myOneLineComponents[i] = new OneLineComponent();

        int startOffset = -1;
        int endOffset = -1;
        if (highlightStartOffset >= 0 && highlightEndOffset > lineOffset && highlightStartOffset < lineOffset + line.length()) {
          startOffset = Math.max(highlightStartOffset - lineOffset, 0);
          endOffset = Math.min(highlightEndOffset - lineOffset, line.length());
        }

        myOneLineComponents[i].setup(line, startOffset, endOffset, isDisabled, strikeout, background);

        if (isDisabledBeforeHighlight) {
          if (highlightStartOffset < 0 || highlightEndOffset > lineOffset) {
            myOneLineComponents[i].setDisabledBeforeHighlight();
          }
        }

        add(myOneLineComponents[i], new GridBagConstraints(0,i,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0));

        lineOffset += line.length();
      }
    }

    public void setDisabled(){
      for (OneLineComponent oneLineComponent : myOneLineComponents) {
        oneLineComponent.setDisabled();
      }
    }
  }


  private class OneLineComponent extends JPanel {
    StrikeoutLabel myLabel1 = new StrikeoutLabel("", SwingConstants.LEFT);
    StrikeoutLabel myLabel2 = new StrikeoutLabel("", SwingConstants.LEFT);
    StrikeoutLabel myLabel3 = new StrikeoutLabel("", SwingConstants.LEFT);

    public OneLineComponent(){
      super(new GridBagLayout());

      myLabel1.setOpaque(true);
      myLabel1.setFont(NORMAL_FONT);

      myLabel2.setOpaque(true);
      myLabel2.setFont(BOLD_FONT);

      myLabel3.setOpaque(true);
      myLabel3.setFont(NORMAL_FONT);

      add(myLabel1, new GridBagConstraints(0,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,0,0),0,0));
      add(myLabel2, new GridBagConstraints(1,0,1,1,0,0,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,0,0),0,0));
      add(myLabel3, new GridBagConstraints(2,0,1,1,1,0,GridBagConstraints.WEST,GridBagConstraints.HORIZONTAL,new Insets(0,0,0,0),0,0));
    }

    public void setup(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout, Color background) {
      myLabel1.setBackground(background);
      myLabel2.setBackground(background);
      myLabel3.setBackground(background);
      setBackground(background);

      myLabel1.setStrikeout(strikeout);
      myLabel2.setStrikeout(strikeout);
      myLabel3.setStrikeout(strikeout);

      if (isDisabled) {
        myLabel1.setText(text);
        myLabel2.setText("");
        myLabel3.setText("");

        setDisabled();
      }
      else {
        myLabel1.setForeground(FOREGROUND_COLOR);
        myLabel2.setForeground(FOREGROUND_COLOR);
        myLabel3.setForeground(FOREGROUND_COLOR);

        if (highlightStartOffset < 0) {
          myLabel1.setText(text);
          myLabel2.setText("");
          myLabel3.setText("");
        }
        else {
          myLabel1.setText(text.substring(0, highlightStartOffset));
          myLabel2.setText(text.substring(highlightStartOffset, highlightEndOffset));
          myLabel3.setText(text.substring(highlightEndOffset));
        }
      }
    }

    public void setDisabled(){
      myLabel1.setForeground(DISABLED_FOREGROUND_COLOR);
      myLabel2.setForeground(DISABLED_FOREGROUND_COLOR);
      myLabel3.setForeground(DISABLED_FOREGROUND_COLOR);
    }

    public void setDisabledBeforeHighlight(){
      myLabel1.setForeground(DISABLED_FOREGROUND_COLOR);
    }


    public Dimension getPreferredSize(){
      myLabel1.setFont(BOLD_FONT);
      myLabel3.setFont(BOLD_FONT);
      Dimension boldPreferredSize = super.getPreferredSize();
      myLabel1.setFont(NORMAL_FONT);
      myLabel3.setFont(NORMAL_FONT);
      Dimension normalPreferredSize = super.getPreferredSize();

      // some fonts (for example, Arial Black Cursiva) have NORMAL characters wider than BOLD characters
      return new Dimension(Math.max(boldPreferredSize.width, normalPreferredSize.width), Math.max(boldPreferredSize.height, normalPreferredSize.height));
    }
  }
}