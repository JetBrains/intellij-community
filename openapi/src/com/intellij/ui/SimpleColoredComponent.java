/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.peer.PeerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This is high performance Swing component which represents an icon
 * with a colored text. The text consists of fragments. Each
 * text fragment has its own color (foreground) and font style.
 *
 * @author Vladimir Kondratyev
 */
public class SimpleColoredComponent extends JComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.SimpleColoredComponent");
  
  private final ArrayList<String> myFragments;
  private final ArrayList<SimpleTextAttributes> myAttributes;

  /**
   * Component's icon. It can be <code>null</code>.
   */
  private Icon myIcon;
  /**
   * Internal padding
   */
  private Insets myIpad;
  /**
   * Gap between icon and text. It is used only if icon is defined.
   */
  private int myIconTextGap;
  /**
   * Defines whether the focus border around the text is painted or not.
   * For example, text can have a border if the component represents a selected item
   * in focused JList.
   */
  private boolean myPaintFocusBorder;
  /**
   * Defines whether the focus border around the text extends to icon or not
   */
  private boolean myFocusBorderAroundIcon;
  /**
   * This is the border around the text. For example, text can have a border
   * if the component represents a selected item in a focused JList.
   * Border can be <code>null</code>.
   */
  private final MyBorder myBorder;

  public SimpleColoredComponent(){
    myFragments=new ArrayList<String>(3);
    myAttributes=new ArrayList<SimpleTextAttributes>(3);
    myIpad = new Insets(1,2,1,2);
    myIconTextGap = 2;
    myBorder = new MyBorder();
    setOpaque(true);
  }

  /**
   * Appends string fragments to existing ones. Appended string
   * will have specified <code>attributes</code>.
   *
   * @exception java.lang.IllegalArgumentException if <code>fragment</code>
   * is <code>null</code>
   * @exception java.lang.IllegalArgumentException if <code>attributes</code>
   * is <code>null</code>
   */
  public void append(final String fragment,final SimpleTextAttributes attributes){
    if(fragment==null){
      throw new IllegalArgumentException("fragment cannot be null");
    }
    if(attributes==null){
      throw new IllegalArgumentException("attributes cannot be null");
    }

    myFragments.add(fragment);
    getAttributes().add(attributes);
  }

  /**
   * Clear all special attributes of <code>SimpleColoredComponent</code>.
   * The are icon, text fragments and their attributes, "paint focus border".
   */
  public void clear(){
    myIcon=null;
    myPaintFocusBorder=false;
    setBorder(null);
    myFragments.clear();
    getAttributes().clear();
  }

  /**
   * @return component's icon. This method returns <code>null</code>
   * if there is no icon.
   */
  public final Icon getIcon(){
    return myIcon;
  }

  /**
   * Sets a new component icon
   */
  public final void setIcon(final Icon icon){
    myIcon=icon;
  }

  /**
   * @return "leave" (internal) internal paddings of the component
   */
  public Insets getIpad(){
    return myIpad;
  }

  /**
   * Sets specified internal paddings
   */
  public void setIpad(final Insets ipad){
    myIpad=ipad;
  }

  /**
   * @return gap between icon and text
   */
  public int getIconTextGap(){
    return myIconTextGap;
  }

  /**
   * Sets a new gap between icon and text
   *
   * @exception java.lang.IllegalArgumentException if the <code>iconTextGap</code>
   * has a negative value
   */
  public void setIconTextGap(final int iconTextGap){
    if(iconTextGap <0){
      throw new IllegalArgumentException("wrong iconTextGap: "+iconTextGap);
    }
    myIconTextGap=iconTextGap;
  }

  /**
   * Sets whether focus border is painted or not
   */
  protected final void setPaintFocusBorder(final boolean paintFocusBorder){
    myPaintFocusBorder=paintFocusBorder;
  }

  /**
   * Sets whether focus border extends to icon or not. If so then
   * component also extends the selection.
   */
  protected final void setFocusBorderAroundIcon(final boolean focusBorderAroundIcon){
    myFocusBorderAroundIcon=focusBorderAroundIcon;
  }

  public Dimension getPreferredSize(){
    // Calculate width
    int width=myIpad.left+myIpad.right;

    if(myIcon!=null){
      width+=myIcon.getIconWidth() + myIconTextGap;
    }

    final Insets borderInsets=myBorder.getBorderInsets(this);
    width+=borderInsets.left+borderInsets.right;

    Font font = getFont();
    LOG.assertTrue(font != null);
    for (int i = myFragments.size() - 1; i >= 0; i--) {
      final SimpleTextAttributes attributes = getAttributes().get(i);
      if (font.getStyle() != attributes.getStyle()) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getStyle());
      }
      final FontMetrics metrics = getFontMetrics(font);
      width += metrics.stringWidth(myFragments.get(i));
    }

    // Calculate height
    int height = myIpad.top + myIpad.bottom;

    final FontMetrics metrics = getFontMetrics(font);
    int textHeight = metrics.getHeight();
    textHeight += borderInsets.top + borderInsets.bottom;

    if(myIcon!=null){
      height+=Math.max(myIcon.getIconHeight(),textHeight);
    }else{
      height+=textHeight;
    }

    // Take into accound that the component itself can have a border
    final Insets insets = getInsets();
    width+=insets.left+insets.right;
    height+=insets.top+insets.bottom;

    return new Dimension(width, height);
  }

  protected void paintComponent(final Graphics g){
    try {
      doPaint(g);
    } catch(RuntimeException e) {
      LOG.error(logSwingPath(), e);
      throw e;
    }
  }

  private void doPaint(final Graphics g) {
    checkCanPaint();
    int xOffset=0;

    // Paint icon and its background
    if(myIcon!=null){
      final Container parent = getParent();
      final Color iconBackgroundColor;
      if(parent!=null && !myFocusBorderAroundIcon){
        iconBackgroundColor=parent.getBackground();
      }
      else{
        iconBackgroundColor=getBackground();
      }
      g.setColor(iconBackgroundColor);
      g.fillRect(0,0,myIcon.getIconWidth() + myIpad.left + myIconTextGap,getHeight());

      myIcon.paintIcon(
        this,
        g,
        myIpad.left,
        (getHeight()-myIcon.getIconHeight())/2
      );

      xOffset+=myIpad.left+myIcon.getIconWidth() + myIconTextGap;
    }

    if (isOpaque()) {
      // Paint text background
      g.setColor(getBackground());
      g.fillRect(
        xOffset,
        0,
        getWidth()-xOffset,
        getHeight()
      );
    }

    // If there is no icon, then we have to add left internal padding
    if(xOffset==0){
      xOffset=myIpad.left;
    }

    // Paint focus border around the text and icon (if necessary)
    if(myPaintFocusBorder){
      if(myFocusBorderAroundIcon || myIcon == null){
        myBorder.paintBorder(this,g,0,0,getWidth(),getHeight());
      }
      else{
        myBorder.paintBorder(this,g,xOffset,0,getWidth()-xOffset,getHeight());
      }
    }
    xOffset+=myBorder.getBorderInsets(this).left;

    // Paint text
    for(int i=0;i<myFragments.size();i++){
      final SimpleTextAttributes attributes=getAttributes().get(i);

      Color color = attributes.getFgColor();
      if(color == null){ // in case if color is not defined we have to get foreground color from Swing hierarchy
        color = getForeground();
      }
      g.setColor(color);

      Font font=getFont();
      if(font.getStyle()!=attributes.getStyle()){ // derive font only if it is necessary
        font=font.deriveFont(attributes.getStyle());
      }
      g.setFont(font);
      final FontMetrics metrics=getFontMetrics(font);

      final String fragment=myFragments.get(i);
      final int textBaseline=(getHeight()-metrics.getHeight())/2 + metrics.getAscent();
      g.drawString(fragment,xOffset,textBaseline);

      final int fragmentWidth = metrics.stringWidth(fragment);

      // 1. Strikeout effect
      if (attributes.isStrikeout()) {
        final int strikeOutAt = textBaseline + (metrics.getDescent() - metrics.getAscent())/2;
        g.drawLine(xOffset, strikeOutAt, xOffset+fragmentWidth, strikeOutAt);
      }
      // 2. Waved effect
      if(attributes.isWaved()){
        if(attributes.getWaveColor() != null){
          g.setColor(attributes.getWaveColor());
        }
        final int wavedAt = textBaseline + 1;
        for(int x = xOffset; x <= xOffset + fragmentWidth; x += 4){
          g.drawLine(x, wavedAt, x + 2, wavedAt + 2);
          g.drawLine( x + 3, wavedAt + 1,  x + 4, wavedAt);
        }
      }

      xOffset+=fragmentWidth;
    }
  }

  private void checkCanPaint() {
    if (!isDisplayable()) {
      LOG.assertTrue(false, logSwingPath());
    }
    final Application application = ApplicationManager.getApplication();
    if (application != null) {
      application.assertIsDispatchThread();
    }
    else if (!SwingUtilities.isEventDispatchThread()){
      throw new RuntimeException(Thread.currentThread().toString());
    }
  }

  private String logSwingPath() {
    final StringBuffer buffer = new StringBuffer("Components hierarchy:\n");
    for (Container c = this; c != null; c = c.getParent()) {
      buffer.append('\n');
      buffer.append(c.toString());
    }
    return buffer.toString();
  }

  public java.util.List<String> getFragments() {
    return Collections.unmodifiableList(myFragments);
  }

  private ArrayList<SimpleTextAttributes> getAttributes() {
    return myAttributes;
  }

  private final class MyBorder implements Border{
    private final Insets myInsets;

    public MyBorder(){
      myInsets=new Insets(1,1,1,1);
    }

    public void paintBorder(final Component c,final Graphics g,final int x,final int y,final int width,final int height){
      g.setColor(Color.BLACK);
      PeerFactory.getInstance().getUIHelper().drawDottedRectangle(g,x,y,x+width-1,y+height-1);
    }

    public Insets getBorderInsets(final Component c){
      return myInsets;
    }

    public boolean isBorderOpaque(){
      return true;
    }
  }
}
