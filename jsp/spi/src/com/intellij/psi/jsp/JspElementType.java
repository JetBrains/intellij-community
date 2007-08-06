/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.jsp;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.jsp.IJspElementType;
import com.intellij.psi.tree.jsp.el.IELElementType;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.peer.PeerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;

/**
 * @author peter
 */
public interface JspElementType extends JspTokenType {
  IElementType HOLDER_TEMPLATE_DATA = new IJspElementType("HOLDER_TEMPLATE_DATA");
  Key<ASTNode> ourContextNodeKey = Key.create("EL.context.node");

  IChameleonElementType JSP_EL_HOLDER = new IChameleonElementType("EL_HOLDER", IELElementType.EL_LANGUAGE){
    public ASTNode parseContents(ASTNode chameleon) {
      final PeerFactory factory = PeerFactory.getInstance();
      final Project project = chameleon.getTreeParent().getPsi().getManager().getProject();
      final PsiBuilder builder = factory.createBuilder(chameleon, getLanguage(), chameleon.getText(), project);
      final PsiParser parser = getLanguage().getParserDefinition().createParser(project);

      builder.putUserData(ourContextNodeKey, chameleon.getTreeParent());
      final ASTNode result = parser.parse(this, builder).getFirstChildNode();
      builder.putUserData(ourContextNodeKey, null);
      return result;
    }

    public boolean isParsable(CharSequence buffer, final Project project) {return false;}
  };
  IElementType JSP_TEMPLATE_EXPRESSION = new IElementType("JSP_TEMPLATE_EXPRESSION", StdLanguages.JAVA);
  IElementType HOLDER_METHOD = new IJspElementType("HOLDER_METHOD");
  IElementType JSP_XML_TEXT = new IJspElementType("JSP_XML_TEXT");
  IElementType JSP_TEMPLATE_STATEMENT = new IElementType("JSP_TEMPLATE_STATEMENT", StdLanguages.JAVA);
  IElementType JSP_CLASS_LEVEL_DECLARATION_STATEMENT = new IElementType("JSP_CLASS_LEVEL_DECLARATION_STATEMENT", StdLanguages.JAVA);
  IElementType JSP_CODE_BLOCK = new IElementType("JSP_CODE_BLOCK", StdLanguages.JAVA);
  IElementType JSP_WHILE_STATEMENT = new IElementType("JSP_DO_WHILE_STATEMENT", StdLanguages.JAVA);
  IElementType JSP_BLOCK_STATEMENT = new IElementType("JSP_BLOCK_STATEMENT", StdLanguages.JAVA);
  IJspElementType JSP_DOCUMENT = new IJspElementType("JSP_DOCUMENT");

  IElementType JSP_SCRIPTLET = JspSpiUtil.createSimpleChameleon("JSP_SCRIPTLET", JspTokenType.JSP_SCRIPTLET_START, JspTokenType.JSP_SCRIPTLET_END, 2);
  IElementType JSP_EXPRESSION = JspSpiUtil.createSimpleChameleon("JSP_EXPRESSION", JspTokenType.JSP_EXPRESSION_START, JspTokenType.JSP_EXPRESSION_END, 3);
  IElementType JSP_DECLARATION = JspSpiUtil.createSimpleChameleon("JSP_DECLARATION_NEW", JspTokenType.JSP_DECLARATION_START, JspTokenType.JSP_DECLARATION_END, 3);

  IFileElementType JSP_TEMPLATE = JspSpiUtil.createTemplateType();

  IElementType JSP_CLASS = new IJspElementType("JSP_CLASS");
  IElementType JSP_METHOD_CALL = new IElementType("JSP_METHOD_CALL", StdLanguages.JAVA);
  IJspElementType JSP_ROOT_TAG = new IJspElementType("JSP_ROOT_TAG");
}
