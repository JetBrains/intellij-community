// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.HTMLJavaHTMLComposer;
import com.intellij.codeInspection.ex.DescriptorComposer;
import com.intellij.codeInspection.ex.HTMLComposerImpl;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.java.JavaBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class DeadHTMLComposer extends HTMLComposerImpl {
  private final InspectionToolPresentation myToolPresentation;
  private final HTMLJavaHTMLComposer myComposer;

  public DeadHTMLComposer(@NotNull InspectionToolPresentation presentation) {
    myToolPresentation = presentation;
    myComposer = getExtension(HTMLJavaHTMLComposer.COMPOSER);
  }

  @Override
  public void compose(@NotNull StringBuilder buf, RefEntity refEntity) {
    compose(buf, refEntity, true);
  }

  public void compose(@NotNull StringBuilder buf, RefEntity refEntity, boolean toExternalHtml) {
    if (toExternalHtml) {
      genPageHeader(buf, refEntity);
    }

    if (refEntity instanceof RefElementImpl) {
      RefElementImpl refElement = (RefElementImpl)refEntity;
      if (refElement.isSuspicious() && !refElement.isEntry()) {
        appendHeading(buf, AnalysisBundle.message("inspection.problem.synopsis"));
        buf.append("<br>");
        buf.append("<div class=\"problem-description\">");
        appendProblemSynopsis(refElement, buf);
        buf.append("</div>");

        if (toExternalHtml) {
          buf.append("<br><br>");
          appendResolution(buf, refElement, DescriptorComposer.quickFixTexts(refElement, myToolPresentation));
        }
        refElement.accept(new RefJavaVisitor() {
          @Override public void visitClass(@NotNull RefClass aClass) {
            appendClassInstantiations(buf, aClass);
            myComposer.appendDerivedClasses(buf, aClass);
            myComposer.appendClassExtendsImplements(buf, aClass);
            myComposer.appendLibraryMethods(buf, aClass);
            myComposer.appendTypeReferences(buf, aClass);
          }

          @Override public void visitMethod(@NotNull RefMethod method) {
            appendElementInReferences(buf, method);
            appendElementOutReferences(buf, method);
            myComposer.appendDerivedMethods(buf, method);
            myComposer.appendDerivedFunctionalExpressions(buf, method);
            myComposer.appendSuperMethods(buf, method);
          }

          @Override public void visitField(@NotNull RefField field) {
            appendElementInReferences(buf, field);
            appendElementOutReferences(buf, field);
          }
        });
      } else {
        appendNoProblems(buf);
      }
      appendCallersList(refElement, buf, new HashSet<>(), true);
    }
  }

  public static void appendProblemSynopsis(final RefElement refElement, @NotNull StringBuilder buf) {
    refElement.accept(new RefJavaVisitor() {
      @Override public void visitField(@NotNull RefField field) {
        if (field.isUsedForReading() && !field.isUsedForWriting()) {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis"));
          return;
        }

        if (!field.isUsedForReading() && field.isUsedForWriting()) {
          if (field.isOnlyAssignedInInitializer()) {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis1"));
            return;
          }

          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis2"));
          return;
        }

        int nUsages = field.getInReferences().size();
        if (nUsages == 0) {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis1"));
        } else if (nUsages == 1) {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis3"));
        } else {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis4", nUsages));
        }
      }

      @Override public void visitClass(@NotNull RefClass refClass) {
        if (refClass.isAnonymous()) {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis10"));
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          String classOrInterface = HTMLJavaHTMLComposer.getClassOrInterface(refClass, true);
          buf.append("&nbsp;");

          int nDerived = getImplementationsCount(refClass);

          if (nDerived == 0) {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis23", classOrInterface));
          } else if (nDerived == 1) {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis24", classOrInterface));
          } else {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis25", classOrInterface, nDerived));
          }
        } else if (refClass.isUtilityClass()) {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis11"));
        } else {
          int nInstantiationsCount = getInstantiationsCount(refClass);

          if (nInstantiationsCount == 0) {
            int nImplementations = getImplementationsCount(refClass);
            if (nImplementations != 0) {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis19", nImplementations));
            } else {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis13"));
            }
          } else if (nInstantiationsCount == 1) {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis12"));
          } else {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis20", nInstantiationsCount));
          }
        }
      }

      @Override public void visitMethod(@NotNull RefMethod method) {
        RefClass refClass = method.getOwnerClass();
        if (method.isExternalOverride()) {
          String classOrInterface = HTMLJavaHTMLComposer.getClassOrInterface(Objects.requireNonNull(refClass), false);
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis22", classOrInterface));
        } else if (method.isStatic() || method.isConstructor()) {
          int nRefs = method.getInReferences().size();
          if (method.isConstructor()) {
            if (nRefs == 0) {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis26.constructor"));
            } else if (method.isConstructor() && ((RefMethodImpl)method).isSuspiciousRecursive()) {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis27.constructor"));
            } else if (nRefs == 1) {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis28.constructor"));
            } else {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis29.constructor", nRefs) );
            }
          } else {
            if (nRefs == 0) {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis26.method"));
            } else if (method.isConstructor() && ((RefMethodImpl)method).isSuspiciousRecursive()) {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis27.method"));
            } else if (nRefs == 1) {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis28.method"));
            } else {
              buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis29.method", nRefs) );
            }
          }
        } else if (refClass instanceof RefClassImpl && ((RefClassImpl)refClass).isSuspicious()) {
          if (method.isAbstract()) {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis14"));
          } else {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis15"));
          }
        } else {
          int nOwnRefs = method.getInReferences().size();
          int nSuperRefs = getSuperRefsCount(method);
          int nDerivedRefs = getDerivedRefsCount(method);

          if (nOwnRefs == 0 && nSuperRefs == 0 && nDerivedRefs == 0) {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis16"));
          } else if (nDerivedRefs > 0 && nSuperRefs == 0 && nOwnRefs == 0) {
            String classOrInterface = refClass == null ? "" : HTMLJavaHTMLComposer.getClassOrInterface(refClass, false);
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis21", classOrInterface));
          } else if (((RefMethodImpl)method).isSuspiciousRecursive()) {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis17"));
          } else {
            buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis18"));
          }
        }
      }
    });
  }

  @Override
  protected void appendAdditionalListItemInfo(@NotNull StringBuilder buf, RefElement refElement) {
    if (refElement instanceof RefImplicitConstructor) {
      refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
    }

    buf.append("<br>");
    if (refElement instanceof RefClassImpl) {
      RefClassImpl refClass = (RefClassImpl)refElement;
      if (refClass.isSuspicious()) {
        if (refClass.isUtilityClass()) {
          // Append nothing.
        } else if (refClass.isAnonymous()) {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis9.suspicious", getInstantiationsCount(refClass)));
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis8.suspicious", getInstantiationsCount(refClass)));
        } else {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis7.suspicious", getInstantiationsCount(refClass)));
        }
      } else {
        if (refClass.isUtilityClass()) {
          // Append nothing.
        } else if (refClass.isAnonymous()) {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis9", getInstantiationsCount(refClass)));
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis8", getInstantiationsCount(refClass)));
        } else {
          buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis7", getInstantiationsCount(refClass)));
        }
      }
    } else {
      int nUsageCount = refElement.getInReferences().size();
      if (refElement instanceof RefMethod) {
        nUsageCount += getDerivedRefsCount((RefMethod) refElement);
      }
      if (((RefElementImpl)refElement).isSuspicious()) {
        buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis6.suspicious", nUsageCount));
      } else {
        buf.append(AnalysisBundle.message("inspection.dead.code.problem.synopsis6", nUsageCount));
      }
    }
  }

  private static int getDerivedRefsCount(RefMethod refMethod) {
    int count = 0;

    for (RefMethod refDerived : refMethod.getDerivedMethods()) {
      count += refDerived.getInReferences().size() + getDerivedRefsCount(refDerived);
    }

    return count;
  }

  private static int getSuperRefsCount(RefMethod refMethod) {
    int count = 0;

    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      count += refSuper.getInReferences().size() + getSuperRefsCount(refSuper);
    }

    return count;
  }

  private static int getInstantiationsCount(RefClass aClass) {
    if (!aClass.isAnonymous()) {
      int count = 0;

      for (RefMethod refConstructor : aClass.getConstructors()) {
        count += refConstructor.getInReferences().size();
      }

      for (RefClass subClass : aClass.getSubClasses()) {
        count += getInstantiationsCount(subClass);
        count -= subClass.getConstructors().size();
      }

      return count;
    }

    return 1;
  }

  private static int getImplementationsCount(RefOverridable refClass) {
    int count = 0;
    for (RefOverridable reference : refClass.getDerivedReferences()) {
      if (reference instanceof RefClass) {
        if (!((RefClass)reference).isInterface() && !((RefClass)reference).isAbstract()) {
          count++;
        }
        count += getImplementationsCount(reference);
      }
      else if (reference instanceof RefFunctionalExpression) {
        count++;
      }
    }

    return count;
  }

  private void appendClassInstantiations(@NotNull StringBuilder buf, RefClass refClass) {
    if (!refClass.isInterface() && !refClass.isAbstract() && !refClass.isUtilityClass()) {
      boolean found = false;

      appendHeading(buf, AnalysisBundle.message("inspection.dead.code.export.results.instantiated.from.heading"));

      startList(buf);
      for (RefMethod refMethod : refClass.getConstructors()) {
        for (RefElement refCaller : refMethod.getInReferences()) {
          appendListItem(buf, refCaller);
          found = true;
        }
      }

      if (!found) {
        startListItem(buf);
        buf.append(AnalysisBundle.message("inspection.dead.code.export.results.no.instantiations.found"));
        doneListItem(buf);
      }

      doneList(buf);
    }
  }

  private void appendCallersList(RefElement element, @NotNull StringBuilder buf, Set<? super RefElement> mentionedElements, boolean appendCallees){
    final Set<RefElement> possibleChildren = getPossibleChildren(element);
    if (!possibleChildren.isEmpty()) {
      if (appendCallees){
        appendHeading(buf, JavaBundle.message("inspection.export.results.callees"));
        buf.append("<div class=\"problem-description\">");
      }
      @NonNls final String ul = "<ul>";
      buf.append(ul);
      for (RefElement refElement : possibleChildren) {
        if (!mentionedElements.contains(refElement)) {
          mentionedElements.add(refElement);
          @NonNls final String li = "<li>";
          buf.append(li);
          appendElementReference(buf, refElement, true);
          @NonNls final String closeLi = "</li>";
          buf.append(closeLi);
          appendCallersList(refElement, buf, mentionedElements, false);
        }
      }
      @NonNls final String closeUl = "</ul>";
      buf.append(closeUl);
      if (appendCallees) {
        buf.append("</div>");
      }
    }
  }

  private static Set<RefElement> getPossibleChildren(RefElement refElement) {
    if (!refElement.isValid()) return Collections.emptySet();

    final HashSet<RefElement> newChildren = new HashSet<>();
    for (RefElement refCallee : refElement.getOutReferences()) {
      if (((RefElementImpl)refCallee).isSuspicious()) {
        newChildren.add(refCallee);
      }
    }

    if (refElement instanceof RefMethod) {
      RefMethod refMethod = (RefMethod) refElement;

      RefClass aClass = refMethod.getOwnerClass();
      if (!refMethod.isStatic() && !refMethod.isConstructor() && (aClass != null && !aClass.isAnonymous())) {
        for (RefOverridable refDerived : refMethod.getDerivedReferences()) {
          if (refDerived instanceof RefMethodImpl && ((RefMethodImpl)refDerived).isSuspicious() ||
              refDerived instanceof RefFunctionalExpressionImpl && ((RefFunctionalExpressionImpl)refDerived).isSuspicious()) {
            newChildren.add(refDerived);
          }
        }
      }
    } else if (refElement instanceof RefClass) {
      RefClass refClass = (RefClass) refElement;
      for (RefClass subClass : refClass.getSubClasses()) {
        if ((subClass.isInterface() || subClass.isAbstract()) && ((RefClassImpl)subClass).isSuspicious()) {
          newChildren.add(subClass);
        }
      }

      if (refClass.getDefaultConstructor() instanceof RefImplicitConstructor) {
        Set<RefElement> fromConstructor = getPossibleChildren(refClass.getDefaultConstructor());
        newChildren.addAll(fromConstructor);
      }
    }

    return newChildren;
  }
}
