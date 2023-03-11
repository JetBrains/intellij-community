// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.codeInsight.completion.MethodTags.Tag.*;

public final class MethodTags {

  @ApiStatus.Experimental
  @NotNull
  public static Set<Tag> tags(@Nullable String referenceName) {
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
      result.add(of(joiner.toString(), firstPart.matcher));
    }
    return result;
  }

  private static Tag[] getTags(String string) {
    return switch (string) {
      case "add" -> new Tag[]{of("put", childOf(CommonClassNames.JAVA_LANG_ITERABLE)),
        of("sum", childOf("java.math.BigDecimal", "java.math.BigInteger")),
        of("plus", childOf("java.math.BigDecimal", "java.math.BigInteger"))};
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
      case "put" -> new Tag[]{of("add", childOf(CommonClassNames.JAVA_UTIL_MAP))};
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

  static class Tag {

    @NotNull
    private final String name;

    @NotNull
    private final Predicate<PsiClass> matcher;

    private Tag(@NotNull String name, @NotNull Predicate<PsiClass> matcher) {
      this.name = name;
      this.matcher = matcher;
    }

    @NotNull
    String getName() {
      return name;
    }

    @NotNull
    Predicate<PsiClass> getMatcher() {
      return matcher;
    }

    static Tag of(@NotNull String name, @NotNull Predicate<PsiClass> matcher) {
      return new Tag(name, matcher);
    }

    static Predicate<PsiClass> any() {
      return t -> true;
    }

    static Predicate<PsiClass> childOf(String... classes) {
      return psiClass -> ContainerUtil.exists(classes, clazz -> InheritanceUtil.isInheritor(psiClass, clazz));
    }

    public static Tag[] anyFrom(String... names) {
      return Arrays.stream(names).map(name -> of(name, any())).toArray(Tag[]::new);
    }
  }
}