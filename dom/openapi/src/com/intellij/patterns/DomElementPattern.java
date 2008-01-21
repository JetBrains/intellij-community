/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class DomElementPattern<T extends DomElement,Self extends DomElementPattern<T,Self>> extends TreeElementPattern<DomElement,T,Self> {
  protected DomElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  protected DomElementPattern(@NotNull final NullablePatternCondition condition) {
    super(condition);
  }

  protected DomElement getParent(@NotNull DomElement t) {
    return t.getParent();
  }

  protected DomElement[] getChildren(@NotNull final DomElement domElement) {
    final List<DomElement> children = new ArrayList<DomElement>();
    domElement.acceptChildren(new DomElementVisitor() {
      public void visitDomElement(final DomElement element) {
        children.add(element);
      }
    });
    return children.toArray(new DomElement[children.size()]);
  }

  public static class Capture<T extends DomElement> extends DomElementPattern<T, Capture<T>> {
    protected Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture() {
      super(new NullablePatternCondition() {
        public boolean accepts(@Nullable final Object o,
                                  final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
          return o instanceof DomElement;
        }
      });
    }
  }

  public Self withChild(@NonNls @NotNull final String localName, final ElementPattern pattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        for (final AbstractDomChildrenDescription description : t.getGenericInfo().getChildrenDescriptions()) {
          if (!(description instanceof DomChildrenDescription) || localName.equals(((DomChildrenDescription)description).getXmlElementName())) {
            for (final DomElement element : description.getValues(t)) {
              if (localName.equals(element.getXmlElementName()) && pattern.getCondition().accepts(element, matchingContext, traverseContext)) {
                return true;
              }
            }
          }
        }
        return false;
      }
    });
  }


}
