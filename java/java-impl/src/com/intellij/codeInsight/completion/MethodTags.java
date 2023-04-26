// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.completion.MethodTags.Tag.*;

public final class MethodTags {

  /**
   * @return LookupElement with support for tags or null if the element doesn't meet tag requirements or can be used without tags
   */
  @Nullable
  static LookupElement wrapLookupWithTags(@NotNull LookupElement element, @NotNull Condition<? super String> matcher) {
    if (matcher.value(element.getLookupString())) {
      return null;
    }
    String lookupString = element.getLookupString();
    PsiElement psiElement = element.getPsiElement();
    if (!(psiElement instanceof PsiMember psiMember)) {
      return null;
    }
    PsiClass psiClass = psiMember.getContainingClass();
    Set<String> tags = tags(lookupString).stream()
      .filter(t -> matcher.value(t.name()) && t.matcher().test(psiClass))
      .map(t -> t.name())
      .collect(Collectors.toSet());
    if (tags.isEmpty()) {
      return null;
    }
    return new TagLookupElementDecorator(element, tags);
  }

  @ApiStatus.Experimental
  static class TagLookupElementDecorator extends LookupElementDecorator<LookupElement> {

    @NotNull
    private final Set<String> myTags;

    protected TagLookupElementDecorator(@NotNull LookupElement delegate, @NotNull Set<String> tags) {
      super(delegate);
      myTags = tags;
    }

    @NotNull
    public Set<String> getTags() {
      return myTags;
    }

    @Override
    public Set<String> getAllLookupStrings() {
      Set<String> all = new HashSet<>(super.getAllLookupStrings());
      all.addAll(myTags);
      return all;
    }

    @Override
    public void renderElement(@NotNull LookupElementPresentation presentation) {
      super.renderElement(presentation);
      if (myTags.size()==1) {
        presentation.appendTailText(" " + JavaBundle.message("java.completion.tag", myTags.size()) + " ", true);
        int startOffset = getStartOffset(presentation);
        String text = myTags.iterator().next();
        presentation.appendTailText(text, true);
        presentation.decorateTailItemTextRange(new TextRange(startOffset, startOffset + text.length()),
                                           LookupElementPresentation.LookupItemDecoration.HIGHLIGHT_MATCHED);
      }
      else if (myTags.size() > 1) {
        presentation.appendTailText(" " + JavaBundle.message("java.completion.tag", myTags.size()) + " ", true);
        Iterator<String> iterator = myTags.iterator();
        String firstTag = iterator.next();
        int startOffset = getStartOffset(presentation);
        presentation.appendTailText(firstTag, true);
        presentation.decorateTailItemTextRange(new TextRange(startOffset, startOffset + firstTag.length()),
                                           LookupElementPresentation.LookupItemDecoration.HIGHLIGHT_MATCHED);
        while (iterator.hasNext()) {
          presentation.appendTailText(", ", true);
          String nextTags = iterator.next();
          startOffset = getStartOffset(presentation);
          presentation.appendTailText(nextTags, true);
          presentation.decorateTailItemTextRange(new TextRange(startOffset, startOffset + firstTag.length()),
                                             LookupElementPresentation.LookupItemDecoration.HIGHLIGHT_MATCHED);
        }
      }
    }

    private static int getStartOffset(@NotNull LookupElementPresentation presentation) {
      return presentation.getTailText() == null ? 0 : presentation.getTailText().length();
    }
  }

  /**
   * It is used to take into account possible tags
   */
  @ApiStatus.Experimental
  static class TagMatcher extends PrefixMatcher {
    @NotNull
    private final PrefixMatcher myMatcher;

    protected TagMatcher(@NotNull PrefixMatcher matcher) {
      super(matcher.getPrefix());
      myMatcher = matcher;
    }

    @Override
    public boolean prefixMatches(@NotNull String name) {
      if (myMatcher.prefixMatches(name)) {
        return true;
      }
      Set<MethodTags.Tag> tags = tags(name);
      for (MethodTags.Tag tag : tags) {
        if (myMatcher.prefixMatches(tag.name())) {
          return true;
        }
      }
      return false;
    }

    @Override
    public @NotNull PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
      PrefixMatcher matcher = myMatcher.cloneWithPrefix(prefix);
      return new TagMatcher(matcher);
    }
  }

  /**
   * @return proposed tags (synonyms), which can be used to extend search
   */
  @ApiStatus.Experimental
  @NotNull
  static Set<Tag> tags(@Nullable String referenceName) {
    if (referenceName == null) {
      return Collections.emptySet();
    }
    String[] strings = NameUtilCore.nameToWords(referenceName);
    if (strings.length == 0) {
      return Collections.emptySet();
    }
    Tag[] canBeFirst = getTags(strings[0]);
    Set<Tag> result = new HashSet<>();
    for (Tag firstPart : canBeFirst) {
      StringJoiner joiner = new StringJoiner("");
      joiner.add(firstPart.name);
      for (int i = 1; i < strings.length; i++) {
        String string = strings[i];
        joiner.add(string);
      }
      result.add(new Tag(joiner.toString(), firstPart.matcher));
    }
    return result;
  }

  private static Tag[] getTags(String string) {
    return switch (string) {
      case "add" -> new Tag[]{new Tag("put", childOf(CommonClassNames.JAVA_LANG_ITERABLE)),
        new Tag("sum", childOf("java.math.BigDecimal", "java.math.BigInteger")),
        new Tag("plus", childOf("java.math.BigDecimal", "java.math.BigInteger"))};
      case "append" -> anyFrom("add");
      case "apply" -> anyFrom("invoke", "do", "call");
      case "assert" -> anyFrom("expect", "verify", "test", "ensure");
      case "build" -> anyFrom("create", "make", "generate");
      case "call" -> anyFrom("execute", "run");
      case "check" -> anyFrom("test", "match");
      case "count" -> anyFrom("size", "length");
      case "convert" -> anyFrom("map");
      case "create" -> anyFrom("build", "make", "generate");
      case "delete" -> anyFrom("remove");
      case "do" -> anyFrom("run", "execute", "call");
      case "execute" -> anyFrom("run", "do", "call");
      case "expect" -> anyFrom("verify", "assert", "test");
      case "from" -> anyFrom("of", "parse");
      case "generate" -> anyFrom("create", "build");
      case "invoke" -> anyFrom("apply", "do", "call");
      case "has" -> anyFrom("contains", "check");
      case "length" -> anyFrom("size", "count");
      case "load" -> anyFrom("read");
      case "match" -> anyFrom("test", "check");
      case "of" -> anyFrom("parse", "from");
      case "parse" -> anyFrom("of", "from");
      case "perform" -> anyFrom("execute", "run", "do");
      case "persist" -> anyFrom("save");
      case "print" -> anyFrom("write");
      case "put" -> new Tag[]{new Tag("add", childOf(CommonClassNames.JAVA_UTIL_MAP))};
      case "remove" -> anyFrom("delete");
      case "run" -> anyFrom("start", "execute", "call");
      case "save" -> anyFrom("persist", "write");
      case "size" -> anyFrom("length", "count");
      case "start" -> anyFrom("call", "execute", "run");
      case "subtract" -> anyFrom("minus");
      case "test" -> anyFrom("check", "match");
      case "validate" -> anyFrom("test", "check");
      case "verify" -> anyFrom("expect", "assert", "test");
      case "write" -> anyFrom("print");
      default -> anyFrom();
    };
  }

  record Tag (@NotNull String name, @NotNull Predicate<PsiClass> matcher) {
    static Predicate<PsiClass> any() {
      return t -> true;
    }

    static Predicate<PsiClass> childOf(String... classes) {
      return psiClass -> ContainerUtil.exists(classes, clazz -> InheritanceUtil.isInheritor(psiClass, clazz));
    }

    public static Tag[] anyFrom(String... names) {
      return Arrays.stream(names).map(name -> new Tag(name, any())).toArray(Tag[]::new);
    }
  }
}