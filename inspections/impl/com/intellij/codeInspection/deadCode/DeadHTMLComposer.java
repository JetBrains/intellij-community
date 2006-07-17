/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 22, 2001
 * Time: 4:58:38 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.HTMLComposer;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.RefElementNode;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DeadHTMLComposer extends HTMLComposer {
  private final InspectionTool myTool;

  public DeadHTMLComposer(InspectionTool tool) {
    myTool = tool;
  }

  public void compose(final StringBuffer buf, RefEntity refEntity) {
    genPageHeader(buf, refEntity);

    if (refEntity instanceof RefElement) {
      RefElementImpl refElement = (RefElementImpl)refEntity;
      if (refElement.isSuspicious() && !refElement.isEntry()) {
        appendHeading(buf, InspectionsBundle.message("inspection.problem.synopsis"));
        //noinspection HardCodedStringLiteral
        buf.append("<br>");
        appendAfterHeaderIndention(buf);
        appendProblemSynopsis(refElement, buf);

        //noinspection HardCodedStringLiteral
        buf.append("<br><br>");
        appendResolution(buf, myTool, refElement);

        refElement.accept(new RefVisitor() {
          public void visitClass(RefClass aClass) {
            appendClassInstantiations(buf, aClass);
            appendDerivedClasses(buf, aClass);
            appendClassExtendsImplements(buf, aClass);
            appendLibraryMethods(buf, aClass);
            appendTypeReferences(buf, aClass);
          }

          public void visitMethod(RefMethod method) {
            appendElementInReferences(buf, method);
            appendElementOutReferences(buf, method);
            appendDerivedMethods(buf, method);
            appendSuperMethods(buf, method);
          }

          public void visitField(RefField field) {
            appendElementInReferences(buf, field);
            appendElementOutReferences(buf, field);
          }
        });
      } else {
        appendNoProblems(buf);        
      }
      appendCallesList(refElement, buf, new HashSet<RefElement>(), true);
    }
  }

  public static void appendProblemSynopsis(final RefElement refElement, final StringBuffer buf) {
    refElement.accept(new RefVisitor() {
      public void visitField(RefField field) {
        if (field.isUsedForReading() && !field.isUsedForWriting()) {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis"));
          return;
        }

        if (!field.isUsedForReading() && field.isUsedForWriting()) {
          if (field.isOnlyAssignedInInitializer()) {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis1"));
            return;
          }

          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis2"));
          return;
        }

        int nUsages = field.getInReferences().size();
        if (nUsages == 0) {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis1"));
        } else if (nUsages == 1) {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis3"));
        } else {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis4", nUsages));
        }
      }

      public void visitClass(RefClass refClass) {
        if (refClass.isAnonymous()) {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis10"));
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          String classOrInterface = getClassOrInterface(refClass, true);
          //noinspection HardCodedStringLiteral
          buf.append("&nbsp;");

          int nDerived = getImplementationsCount(refClass);

          if (nDerived == 0) {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis23", classOrInterface));
          } else if (nDerived == 1) {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis24", classOrInterface));
          } else {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis25", classOrInterface, nDerived));
          }
        } else if (refClass.isUtilityClass()) {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis11"));
        } else {
          int nInstantiationsCount = getInstantiationsCount(refClass);

          if (nInstantiationsCount == 0) {
            int nImplementations = getImplementationsCount(refClass);
            if (nImplementations != 0) {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis19", nImplementations));
            } else {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis13"));
            }
          } else if (nInstantiationsCount == 1) {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis12"));
          } else {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis20", nInstantiationsCount));
          }
        }
      }

      public void visitMethod(RefMethod method) {
        RefClass refClass = method.getOwnerClass();
        if (method.isExternalOverride()) {
          String classOrInterface = getClassOrInterface(refClass, false);
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis22", classOrInterface));
        } else if (method.isStatic() || method.isConstructor()) {
          int nRefs = method.getInReferences().size();
          if (method.isConstructor()) {
            if (nRefs == 0) {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis26.constructor"));
            } else if (method.isConstructor() && ((RefMethodImpl)method).isSuspiciousRecursive()) {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis27.constructor"));
            } else if (nRefs == 1) {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis28.constructor"));
            } else {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis29.constructor", nRefs) );
            }
          } else {
            if (nRefs == 0) {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis26.method"));
            } else if (method.isConstructor() && ((RefMethodImpl)method).isSuspiciousRecursive()) {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis27.method"));
            } else if (nRefs == 1) {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis28.method"));
            } else {
              buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis29.method", nRefs) );
            }
          }
        } else if (((RefClassImpl)refClass).isSuspicious()) {
          if (method.isAbstract()) {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis14"));
          } else {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis15"));
          }
        } else {
          int nOwnRefs = method.getInReferences().size();
          int nSuperRefs = getSuperRefsCount(method);
          int nDerivedRefs = getDerivedRefsCount(method);

          if (nOwnRefs == 0 && nSuperRefs == 0 && nDerivedRefs == 0) {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis16"));
          } else if (nDerivedRefs > 0 && nSuperRefs == 0 && nOwnRefs == 0) {
            String classOrInterface = getClassOrInterface(refClass, false);
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis21", classOrInterface));
          } else if (((RefMethodImpl)method).isSuspiciousRecursive()) {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis17"));
          } else {
            buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis18"));
          }
        }
      }
    });
  }

  protected void appendAdditionalListItemInfo(StringBuffer buf, RefElement refElement) {
    if (refElement instanceof RefImplicitConstructor) {
      refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
    }

    //noinspection HardCodedStringLiteral
    buf.append("<br><font style=\"font-family:verdana;color:#808080\">");
    if (refElement instanceof RefClass) {
      RefClassImpl refClass = (RefClassImpl)refElement;
      if (refClass.isSuspicious()) {
        if (refClass.isUtilityClass()) {
          // Append nothing.
        } else if (refClass.isAnonymous()) {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis9.suspicious", getInstantiationsCount(refClass)));
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis8.suspicious", getInstantiationsCount(refClass)));
        } else {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis7.suspicious", getInstantiationsCount(refClass)));
        }
      } else {
        if (refClass.isUtilityClass()) {
          // Append nothing.
        } else if (refClass.isAnonymous()) {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis9", getInstantiationsCount(refClass)));
        } else if (refClass.isInterface() || refClass.isAbstract()) {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis8", getInstantiationsCount(refClass)));
        } else {
          buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis7", getInstantiationsCount(refClass)));
        }
      }
    } else {
      int nUsageCount = refElement.getInReferences().size();
      if (refElement instanceof RefMethod) {
        nUsageCount += getDerivedRefsCount((RefMethod) refElement);
      }
      if (((RefElementImpl)refElement).isSuspicious()) {
        buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis6.suspicious", nUsageCount));
      } else {
        buf.append(InspectionsBundle.message("inspection.dead.code.problem.synopsis6", nUsageCount));
      }
    }

    //noinspection HardCodedStringLiteral
    buf.append("</font>");
  }

  private static int getDerivedRefsCount(RefMethod refMethod) {
    int count = 0;

    for (Iterator<RefMethod> iterator = refMethod.getDerivedMethods().iterator(); iterator.hasNext();) {
      RefMethod refDerived = iterator.next();
      count += refDerived.getInReferences().size() + getDerivedRefsCount(refDerived);
    }

    return count;
  }

  private static int getSuperRefsCount(RefMethod refMethod) {
    int count = 0;

    for (Iterator<RefMethod> iterator = refMethod.getSuperMethods().iterator(); iterator.hasNext();) {
      RefMethod refSuper = iterator.next();
      count += refSuper.getInReferences().size() + getSuperRefsCount(refSuper);
    }

    return count;
  }

  private static int getInstantiationsCount(RefClass aClass) {
    if (!aClass.isAnonymous()) {
      int count = 0;

      for (Iterator<RefMethod> iterator = aClass.getConstructors().iterator(); iterator.hasNext();) {
        RefMethod refConstructor = iterator.next();
        count += refConstructor.getInReferences().size();
      }

      for (Iterator<RefClass> iterator = aClass.getSubClasses().iterator(); iterator.hasNext();) {
        RefClass subClass = iterator.next();
        count += getInstantiationsCount(subClass);
        count -= subClass.getConstructors().size();
      }

      return count;
    }

    return 1;
  }

  private static int getImplementationsCount(RefClass refClass) {
    int count = 0;
    for (Iterator<RefClass> iterator = refClass.getSubClasses().iterator(); iterator.hasNext();) {
      RefClass subClass = iterator.next();
      if (!subClass.isInterface() && !subClass.isAbstract()) {
        count++;
      }
      count += getImplementationsCount(subClass);
    }

    return count;
  }

  private void appendClassInstantiations(StringBuffer buf, RefClass refClass) {
    if (!refClass.isInterface() && !refClass.isAbstract() && !refClass.isUtilityClass()) {
      boolean found = false;

      appendHeading(buf, InspectionsBundle.message("inspection.dead.code.export.results.instantiated.from.heading"));

      startList(buf);
      for (Iterator<RefMethod> iterator = refClass.getConstructors().iterator(); iterator.hasNext();) {
        RefMethod refMethod = iterator.next();
        for (Iterator<RefElement> constructorCallersIterator = refMethod.getInReferences().iterator(); constructorCallersIterator.hasNext();) {
          RefElement refCaller = constructorCallersIterator.next();
          appendListItem(buf, refCaller);
          found = true;
        }
      }

      if (!found) {
        startListItem(buf);
        buf.append(InspectionsBundle.message("inspection.dead.code.export.results.no.instantiations.found"));
        doneListItem(buf);
      }

      doneList(buf);
    }
  }

  private void appendCallesList(RefElement element, StringBuffer buf, Set<RefElement> mentionedElements, boolean appendCallees){
    final Set<RefElement> possibleChildren = new RefElementNode(element, myTool).getPossibleChildren(element);
    if (possibleChildren.size() > 0) {
      if (appendCallees){
        appendHeading(buf, InspectionsBundle.message("inspection.export.results.callees"));
        @NonNls String font = "<font style=\"font-family:verdana;\" size = \"3\">";
        buf.append(font);
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
          appendCallesList(refElement, buf, mentionedElements, false);
        }
      }
      @NonNls final String closeUl = "</ul>";
      buf.append(closeUl);
      if (appendCallees){
        @NonNls String closeFont = "</font>";
        buf.append(closeFont);
      }
    }
  }
}
