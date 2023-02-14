// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.editorActions.TabOutScopesTracker;
import com.intellij.diagnostic.CoreAttachmentFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PsiTypeLookupItem extends LookupItem implements TypedLookupItem {
  private static final InsertHandler<PsiTypeLookupItem> DEFAULT_IMPORT_FIXER = new InsertHandler<>() {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull PsiTypeLookupItem item) {
      if (item.getObject() instanceof PsiClass) {
        addImportForItem(context, (PsiClass)item.getObject());
      }
    }
  };

  private static final Logger LOG = Logger.getInstance(PsiTypeLookupItem.class);
  public static final ClassConditionKey<PsiTypeLookupItem> CLASS_CONDITION_KEY = ClassConditionKey.create(PsiTypeLookupItem.class);
  private final boolean myDiamond;
  private final int myBracketsCount;
  private boolean myIndicateAnonymous;
  private final InsertHandler<PsiTypeLookupItem> myImportFixer;
  @NotNull private final PsiSubstitutor mySubstitutor;
  private boolean myAddArrayInitializer;
  private String myLocationString = "";
  private final String myForcedPresentableName;

  private PsiTypeLookupItem(Object o, @NotNull @NonNls String lookupString, boolean diamond, int bracketsCount, InsertHandler<PsiTypeLookupItem> fixer,
                            @NotNull PsiSubstitutor substitutor) {
    super(o, lookupString);
    myDiamond = diamond;
    myBracketsCount = bracketsCount;
    myImportFixer = fixer;
    mySubstitutor = substitutor;
    myForcedPresentableName = o instanceof PsiClass && !lookupString.equals(((PsiClass)o).getName()) ? lookupString : null;
  }

  @NotNull
  @Override
  public PsiType getType() {
    Object object = getObject();
    PsiType type = object instanceof PsiType
                   ? getSubstitutor().substitute((PsiType)object)
                   : JavaPsiFacade.getElementFactory(((PsiClass) object).getProject()).createType((PsiClass)object, getSubstitutor());
    for (int i = 0; i < getBracketsCount(); i++) {
      type = new PsiArrayType(type);
    }
    return type;
  }

  @Nullable
  public String getForcedPresentableName() {
    return myForcedPresentableName;
  }

  public void setIndicateAnonymous(boolean indicateAnonymous) {
    myIndicateAnonymous = indicateAnonymous;
  }

  public boolean isIndicateAnonymous() {
    return myIndicateAnonymous;
  }

  @Override
  public boolean equals(final Object o) {
    return super.equals(o) && o instanceof PsiTypeLookupItem &&
           getBracketsCount() == ((PsiTypeLookupItem) o).getBracketsCount() &&
           myAddArrayInitializer == ((PsiTypeLookupItem) o).myAddArrayInitializer;
  }

  public boolean isAddArrayInitializer() {
    return myAddArrayInitializer;
  }

  public void setAddArrayInitializer() {
    myAddArrayInitializer = true;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    SmartPsiElementPointer<PsiElement> pointer = null;
    if (getObject() instanceof PsiElement psiElement) {
      pointer = SmartPointerManager.getInstance(context.getProject()).createSmartPsiElementPointer(psiElement);
    }
    myImportFixer.handleInsert(context, this);
    // restore PSI element tucked in this.myObject and possibly made invalid by import fixer
    if (pointer != null) {
      setObject(pointer.getElement());
    }

    PsiElement position = context.getFile().findElementAt(context.getStartOffset());
    boolean insideVarDeclaration = position.getParent() instanceof PsiTypeElement typeElement &&
                                   typeElement.getParent() instanceof PsiVariable;
    if (position != null) {
      int genericsStart = context.getTailOffset();
      context.getDocument().insertString(genericsStart, JavaCompletionUtil.escapeXmlIfNeeded(context, calcGenerics(position, context)));
      JavaCompletionUtil.shortenReference(context.getFile(), genericsStart - 1);
    }

    int targetOffset = context.getTailOffset();
    String braces = StringUtil.repeat("[]", getBracketsCount());
    Editor editor = context.getEditor();
    if (!braces.isEmpty()) {
      if (myAddArrayInitializer) {
        context.getDocument().insertString(targetOffset, braces + "{}");
        targetOffset += braces.length() + 1;
      } else {
        context.getDocument().insertString(targetOffset, braces);
        targetOffset += insideVarDeclaration ? braces.length() : 1;
        if (context.getCompletionChar() == '[') {
          context.setAddCompletionChar(false);
        }
      }
      TabOutScopesTracker.getInstance().registerEmptyScope(editor, targetOffset);
    }
    editor.getCaretModel().moveToOffset(targetOffset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    InsertHandler handler = getInsertHandler();
    if (handler != null) {
      //noinspection unchecked
      handler.handleInsert(context, this);
    }
  }

  @NotNull
  public String calcGenerics(@NotNull PsiElement context, InsertionContext insertionContext) {
    if (insertionContext.getCompletionChar() == '<') {
      return "";
    }

    assert context.isValid();
    if (myDiamond) {
      return "<>";
    }

    if (getObject() instanceof PsiClass psiClass) {
      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper();
      PsiSubstitutor substitutor = getSubstitutor();
      StringBuilder builder = new StringBuilder();
      for (PsiTypeParameter parameter : psiClass.getTypeParameters()) {
        PsiType substitute = substitutor.substitute(parameter);
        if (substitute == null ||
            (PsiUtil.resolveClassInType(substitute) == parameter &&
             resolveHelper.resolveReferencedClass(parameter.getName(), context) != CompletionUtil.getOriginalOrSelf(parameter))) {
          return "";
        }
        if (builder.length() > 0) {
          builder.append(", ");
        }
        builder.append(substitute.getCanonicalText());
      }
      if (builder.length() > 0) {
        return "<" + builder + ">";
      }
    }
    return "";
  }

  @Override
  public int hashCode() {
    final int fromSuper = super.hashCode();
    final int dim = getBracketsCount();
    return fromSuper + dim * 31;
  }

  public int getBracketsCount() {
    return myBracketsCount;
  }

  public static PsiTypeLookupItem createLookupItem(@NotNull PsiType type, @Nullable PsiElement context) {
    final boolean diamond = isDiamond(type);
    return createLookupItem(type, context, diamond);
  }

  public static PsiTypeLookupItem createLookupItem(@NotNull PsiType type, @Nullable PsiElement context, boolean isDiamond) {
    return createLookupItem(type, context, isDiamond, DEFAULT_IMPORT_FIXER);
  }


  public static PsiTypeLookupItem createLookupItem(@NotNull PsiType type, @Nullable PsiElement context, boolean isDiamond, InsertHandler<PsiTypeLookupItem> importFixer) {
    int dim = 0;
    while (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
      dim++;
    }

    return doCreateItem(type, context, dim, isDiamond, importFixer);
  }

  private static PsiTypeLookupItem doCreateItem(final PsiType type,
                                                PsiElement context,
                                                int bracketsCount,
                                                boolean diamond,
                                                InsertHandler<PsiTypeLookupItem> importFixer) {
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass psiClass = classResolveResult.getElement();

      if (psiClass != null) {
        String name = psiClass.getName();
        if (name != null) {
          PsiClass resolved = JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper().resolveReferencedClass(name, context);
          String[] allStrings;
          if (!psiClass.getManager().areElementsEquivalent(resolved, psiClass)) {
            // inner class name should be shown qualified if its not accessible by single name
            allStrings = ArrayUtilRt.toStringArray(JavaCompletionUtil.getAllLookupStrings(psiClass));
          } else {
            allStrings = new String[]{name};
          }
          String lookupString = allStrings[allStrings.length - 1];

          PsiTypeLookupItem item = new PsiTypeLookupItem(psiClass, lookupString, diamond, bracketsCount, importFixer,
                                                         classResolveResult.getSubstitutor());
          item.addLookupStrings(allStrings);
          return item;
        }
      }

    }
    return new PsiTypeLookupItem(type, type.getPresentableText(), false, bracketsCount, importFixer, PsiSubstitutor.EMPTY);
  }

  public static boolean isDiamond(PsiType type) {
    boolean diamond = false;
    if (type instanceof PsiClassReferenceType) {
      final PsiReferenceParameterList parameterList = ((PsiClassReferenceType)type).getReference().getParameterList();
      if (parameterList != null) {
        final PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
        diamond = typeParameterElements.length == 1 && typeParameterElements[0].getType() instanceof PsiDiamondType;
      }
    }
    return diamond;
  }

  @NotNull
  private PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    final Object object = getObject();
    if (object instanceof PsiClass) {
      JavaPsiClassReferenceElement.renderClassItem(presentation, this, (PsiClass)object, myDiamond, myLocationString, mySubstitutor);
    } else {
      assert object instanceof PsiType;

      if (!(object instanceof PsiPrimitiveType)) {
        presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this));
      }

      presentation.setItemText(((PsiType)object).getCanonicalText());
      presentation.setItemTextBold(object instanceof PsiPrimitiveType);
      if (isAddArrayInitializer()) {
        presentation.setTailText("{...}");
      }

    }
    if (myBracketsCount > 0) {
      List<LookupElementPresentation.TextFragment> tail = new ArrayList<>(presentation.getTailFragments());
      presentation.clearTail();
      presentation.appendTailText(StringUtil.repeat("[]", myBracketsCount), false);
      for (LookupElementPresentation.TextFragment fragment : tail) {
        presentation.appendTailText(fragment.text, fragment.isGrayed());
      }
    }
  }

  public PsiTypeLookupItem setShowPackage() {
    Object object = getObject();
    if (object instanceof PsiClass) {
      myLocationString = " (" + PsiFormatUtil.getPackageDisplayName((PsiClass)object) + ")";
    }
    return this;
  }

  public static void addImportForItem(InsertionContext context, PsiClass aClass) {
    if (aClass.getQualifiedName() == null) return;
    PsiFile file = context.getFile();

    int startOffset = context.getStartOffset();
    int tail = context.getTailOffset();

    PsiJavaCodeReferenceElement ref =
      PsiTreeUtil.findElementOfClassAtOffset(file, tail - 1, PsiJavaCodeReferenceElement.class, false);
    boolean goneDeeper = false;
    while (ref != null) {
      PsiElement qualifier = ref.getQualifier();
      PsiClass outer = aClass.getContainingClass();
      if (!Objects.equals(aClass.getName(), ref.getReferenceName())) {
        if (!JavaPsiFacade.getInstance(context.getProject()).getResolveHelper().isAccessible(aClass, ref, outer)) {
          // An inner class of non-public superclass is accessed via public subclass: do not rationalize qualifier in this case
          return;
        }
        break;
      }
      if (!(qualifier instanceof PsiJavaCodeReferenceElement) || outer == null) break;

      goneDeeper = true;
      ref = (PsiJavaCodeReferenceElement)qualifier;
      aClass = outer;
    }

    int newTail = JavaCompletionUtil.insertClassReference(aClass, file,
                                                          goneDeeper ? ref.getTextRange().getStartOffset() : startOffset,
                                                          goneDeeper ? ref.getTextRange().getEndOffset() : tail);
    if (newTail > context.getDocument().getTextLength() || newTail < 0) {
      LOG.error("Invalid offset after insertion\n" +
                "offset=" + newTail + "\n" +
                "start=" + startOffset + "\n" +
                "tail=" + tail + "\n" +
                "file.length=" + file.getTextLength() + "\n" +
                "document=" + context.getDocument() + "\n" +
                new Throwable(),
                CoreAttachmentFactory.createAttachment(context.getDocument()));
      return;
    }

    if (!goneDeeper) {
      context.setTailOffset(newTail);
    }
  }
}
