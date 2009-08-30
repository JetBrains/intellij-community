package com.intellij.refactoring.typeCook.deductive.builder;

import com.intellij.psi.PsiType;
import com.intellij.refactoring.typeCook.deductive.resolver.Binding;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jul 20, 2004
 * Time: 6:02:29 PM
 * To change this template use File | Settings | File Templates.
 */
public class Subtype extends Constraint {
  public Subtype(PsiType left, PsiType right) {
    super(left, right);
  }

  String relationString() {
    return "<:";
  }

  int relationType() {
    return 1;
  }

  public Constraint apply(final Binding b) {
    return new Subtype(b.apply(myLeft), b.apply(myRight));
  }
}
