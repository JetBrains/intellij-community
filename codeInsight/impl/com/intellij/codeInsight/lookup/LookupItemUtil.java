package com.intellij.codeInsight.lookup;

import com.intellij.aspects.psi.PsiPointcutDef;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.template.Template;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiUtil;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.02.2003
 * Time: 16:05:20
 * To change this template use Options | File Templates.
 */
public class LookupItemUtil{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.lookup.LookupItemUtil");

  public static LookupItem addLookupItem(Set<LookupItem> set, Object object, String prefix) {
    LOG.assertTrue(object != null, "Lookup item can't be null!");
    return addLookupItem(set, object, prefix, -1);
  }

  public static LookupItem addLookupItem(Set<LookupItem> set, Object object, String prefix, InsertHandler handler) {
    LOG.assertTrue(object != null, "Lookup item can't be null!");
    LookupItem item = addLookupItem(set, object, prefix, -1);
    if (item != null) {
      item.setAttribute(LookupItem.INSERT_HANDLER_ATTR, handler);
    }
    return item;
  }

  public static LookupItem addLookupItem(Set<LookupItem> set, Object object, String prefix, int tailType) {
    LOG.assertTrue(object != null, "Lookup item can't be null!");
    //TODO[ik]: remove the check: it is always false?
    if (object instanceof PsiType) {
      PsiType psiType = (PsiType)object;
      for (final LookupItem lookupItem : set) {
        Object o = lookupItem.getObject();
        if (o.equals(psiType)) {
          return lookupItem;
        }
      }
    }

    LookupItem item = LookupItemUtil.objectToLookupItem(object);
    String text = item.getLookupString();
    for (Object o : set) {
      if(o instanceof LookupItem) {
        final LookupItem lookupItem = (LookupItem)o;
        if(lookupItem.getObject().equals(o)) return null;
      }
    }
    if (CompletionUtil.startsWith(text, prefix)) {
      item.setLookupString(text);
      if (tailType >= 0) {
        item.setAttribute(CompletionUtil.TAIL_TYPE_ATTR, new Integer(tailType));
      }
      return set.add(item) ? item : null;
    }
    else{
      return null;
    }
  }

  public static void addLookupItems(Set<LookupItem> set, Object[] objects, String prefix) {
    for (Object object : objects) {
      LOG.assertTrue(object != null, "Lookup item can't be null!");
      addLookupItem(set, object, prefix);
    }
  }

  public static void removeLookupItem(Set<LookupItem> set, Object object) {
    Iterator iter = set.iterator();
    while (iter.hasNext()) {
      LookupItem item = (LookupItem)iter.next();
      if (object.equals(item.getObject())) {
        iter.remove();
        break;
      }
    }
  }

  public static boolean containsItem(Set<LookupItem> set, Object object) {
    for (final Object aSet : set) {
      final LookupItem item = (LookupItem)aSet;
      if (object != null && object.equals(item.getObject())) {
        return true;
      }
    }
    return false;
  }

  public static LookupItem objectToLookupItem(Object object) {
    if (object instanceof LookupItem) return (LookupItem)object;
    
    String s = null;
    LookupItem item = new LookupItem(object, "");
    int tailType = TailType.NONE;
    if (object instanceof PsiElement){
      PsiElement element = (PsiElement) object;
      if(element.getUserData(CompletionUtil.ORIGINAL_KEY) != null){
        element = element.getUserData(CompletionUtil.ORIGINAL_KEY);
        object = element;
        item = new LookupItem(object, "");
      }
      s = PsiUtil.getName(element);
    }
    if (object instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)object;
      s = method.getName();
      PsiType type = method.getReturnType();
      if (type == PsiType.VOID) {
        tailType = TailType.SEMICOLON;
      }
    }
    else if (object instanceof PsiPackage) {
      tailType = TailType.DOT;
    }
    else if (object instanceof PsiKeyword) {
      s = ((PsiKeyword)object).getText();
    }
    else if (object instanceof PsiExpression) {
      s = ((PsiExpression)object).getText();
    }
    else if (object instanceof PsiType) {
      final PsiType type = (PsiType) object;
      if (type instanceof PsiPrimitiveType) {
        s = ((PsiType)object).getPresentableText();
      }
      else if (type instanceof PsiArrayType) {
        PsiType contentType = type;
        int dim = 0;
        final StringBuffer tail = new StringBuffer();
        while (contentType instanceof PsiArrayType) {
          contentType = ((PsiArrayType)contentType).getComponentType();
          tail.append("[]");
          dim++;
        }
        if (contentType instanceof PsiClassType) {
          PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)contentType).resolveGenerics();
          final PsiClass psiClass = classResolveResult.getElement();
          final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
          final String text;
          if (psiClass != null) {
            text = formatTypeName(psiClass, substitutor);
          }
          else {
            text = type.getPresentableText();
          }
          String typeString = text;
          if (text.indexOf('<') > 0) {
            typeString = text.substring(0, text.indexOf('<'));
          }
          s = text.substring(typeString.lastIndexOf('.') + 1);
          item = psiClass != null ? new LookupItem(psiClass, s) : new LookupItem(text, s);
          item.setAttribute(LookupItem.SUBSTITUTOR, substitutor);
        }
        else {
          item = new LookupItem(contentType, "");
          s = contentType.getPresentableText();
        }
        item.setAttribute(LookupItem.TAIL_TEXT_ATTR, " " + tail.toString() + "");
        item.setAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR, "");
        item.setAttribute(LookupItem.BRACKETS_COUNT_ATTR, new Integer(dim));
      }
      else if (type instanceof PsiClassType) {
        PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)type).resolveGenerics();
        final PsiClass psiClass = classResolveResult.getElement();
        final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
        final String text;
        if (psiClass != null) {
          text = formatTypeName(psiClass, substitutor);
        }
        else {
          text = type.getPresentableText();
        }
        if (text != null) {
          String typeString = text;
          if (text.indexOf('<') > 0) {
            typeString = text.substring(0, text.indexOf('<'));
          }
          s = text.substring(typeString.lastIndexOf('.') + 1);

          item = psiClass != null ? new LookupItem(psiClass, s) : new LookupItem(text, s);
          item.setAttribute(LookupItem.SUBSTITUTOR, substitutor);
        }
        else {
          s = type.getPresentableText();
        }
      }
      else {
        s = type.getPresentableText();
      }
      item.setAttribute(LookupItem.TYPE, type);
    }
    else if (object instanceof PsiMetaData) {
      s = ((PsiMetaData)object).getName();
    }
    else if (object instanceof String) {
      s = (String)object;
    }
    else if (object instanceof Template) {
      s = "";
    }
    else if (object instanceof PsiPointcutDef) {
      s = ((PsiPointcutDef)object).getName();
    } else if (object instanceof PresentableLookupValue) {
      s = ((PresentableLookupValue)object).getPresentation();
    }

    if (s == null) {
      LOG.assertTrue(false, "Null string for object: " + object + " of class " + (object != null ?object.getClass():null));
    }
    item.setLookupString(s);
    item.setAttribute(CompletionUtil.TAIL_TYPE_ATTR, new Integer(tailType));
    return item;
  }

  public static String formatTypeName(final PsiClass element, final PsiSubstitutor substitutor) {
    final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(element.getProject());
    String name = element.getName();
    if(substitutor != null){
      final PsiTypeParameter[] params = element.getTypeParameters();
      if(params.length > 0){
        boolean flag = true;
        StringBuffer buffer = new StringBuffer();
        buffer.append("<");
        for(int i = 0; i < params.length; i++){
          final PsiTypeParameter param = params[i];
          final PsiType type = substitutor.substitute(param);
          if(type == null){
            flag = false;
            break;
          }
          buffer.append(type.getPresentableText());
          if(i < params.length - 1){ buffer.append(",");
            if(styleSettings.SPACE_AFTER_COMMA) buffer.append(" ");
          }
        }
        buffer.append(">");
        if(flag) name += buffer;
      }
    }
    return name;
  }
}