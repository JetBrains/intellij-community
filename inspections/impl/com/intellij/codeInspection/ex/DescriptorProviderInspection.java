package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.*;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jdom.IllegalDataException;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author max
 */
public abstract class DescriptorProviderInspection extends InspectionTool implements ProblemDescriptionsProcessor {
  private HashMap<RefEntity, CommonProblemDescriptor[]> myProblemElements;
  private HashMap<String, Set<RefElement>> myPackageContents = null;
  private HashSet<RefModule> myModulesProblems = null;
  private HashMap<CommonProblemDescriptor,RefEntity> myProblemToElements;
  private DescriptorComposer myComposer;
  private HashMap<RefEntity, Set<QuickFix>> myQuickFixActions;


  protected DescriptorProviderInspection() {
    myProblemElements = new HashMap<RefEntity, CommonProblemDescriptor[]>();
    myProblemToElements = new HashMap<CommonProblemDescriptor, RefEntity>();
    myQuickFixActions = new HashMap<RefEntity, Set<QuickFix>>();
  }

  public void addProblemElement(RefEntity refElement, CommonProblemDescriptor[] descriptions) {
    if (refElement == null) return;
    if (descriptions == null || descriptions.length == 0) return;
    myProblemElements.put(refElement, descriptions);
    for (CommonProblemDescriptor description : descriptions) {
      myProblemToElements.put(description, refElement);
      collectQuickFixes(description.getFixes(), refElement);
    }
  }

  private void collectQuickFixes(final QuickFix[] fixes, final RefEntity refEntity) {
    if (fixes != null) {
      Set<QuickFix> localQuickFixes = myQuickFixActions.get(refEntity);
      if (localQuickFixes == null) {
        localQuickFixes = new HashSet<QuickFix>();
        myQuickFixActions.put(refEntity, localQuickFixes);
      }
      localQuickFixes.addAll(Arrays.asList(fixes));
    }
  }

  public void ignoreElement(RefEntity refEntity) {
    if (refEntity == null) return;
    myProblemElements.remove(refEntity);
    myQuickFixActions.remove(refEntity);
  }

  public void ignoreProblem(ProblemDescriptor problem) {
    RefEntity refElement = myProblemToElements.get(problem);
    if (refElement != null) ignoreProblem(refElement, problem, -1);
  }

  public void ignoreProblem(RefEntity refEntity, CommonProblemDescriptor problem, int idx) {
    if (refEntity == null) return;
    final Set<QuickFix> localQuickFixes = myQuickFixActions.get(refEntity);
    final QuickFix[] fixes = problem.getFixes();
    if (isIgnoreProblem(fixes, localQuickFixes, idx)){
      myProblemToElements.remove(problem);
      CommonProblemDescriptor[] descriptors = myProblemElements.get(refEntity);
      if (descriptors != null) {
        ArrayList<CommonProblemDescriptor> newDescriptors = new ArrayList<CommonProblemDescriptor>(Arrays.asList(descriptors));
        newDescriptors.remove(problem);
        myQuickFixActions.put(refEntity, null);
        if (newDescriptors.size() > 0) {
          myProblemElements.put(refEntity, newDescriptors.toArray(new ProblemDescriptor[newDescriptors.size()]));
          for (CommonProblemDescriptor descriptor : newDescriptors) {
            collectQuickFixes(descriptor.getFixes(), refEntity);
          }
        }
        else {
          myProblemElements.remove(refEntity);
        }
      }
    }
  }

  private static boolean isIgnoreProblem(QuickFix[] problemFixes, Set<QuickFix> fixes, int idx){
    if (problemFixes == null || fixes == null) {
      return true;
    }
    if (problemFixes.length <= idx){
      return true;
    }
    for (QuickFix fix : problemFixes) {
      if (fix != problemFixes[idx] && !fixes.contains(fix)){
        return false;
      }
    }
    return true;
  }

  public void cleanup() {
    super.cleanup();
    myProblemElements.clear();
    myProblemToElements.clear();
    myQuickFixActions.clear();
    myPackageContents = null;
    myModulesProblems = null;
  }

  public CommonProblemDescriptor[] getDescriptions(RefEntity refEntity) {
    if (refEntity instanceof RefElement && !((RefElement)refEntity).isValid()) {
      ignoreElement(refEntity);
      return null;
    }

    return myProblemElements.get(refEntity);
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new DescriptorComposer(this);
    }
    return myComposer;
  }

  public void exportResults(final Element parentNode) {
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(final RefEntity refEntity) {
        if (myProblemElements.containsKey(refEntity)) {
          CommonProblemDescriptor[] descriptions = getDescriptions(refEntity);
          for (CommonProblemDescriptor description : descriptions) {
            @NonNls final String template = description.getDescriptionTemplate();
            int line = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getLineNumber() : -1;
            final String text = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getPsiElement().getText() : "";
            @NonNls String problemText = template.replaceAll("#ref", text.replaceAll("\\$", "\\\\\\$"));
            problemText = problemText.replaceAll(" #loc ", " ");

            Element element = XMLExportUtl.createElement(refEntity, parentNode, line);
            Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));
            problemClassElement.addContent(getDisplayName());
            element.addContent(problemClassElement);
            try {
              Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
              descriptionElement.addContent(problemText);
              element.addContent(descriptionElement);
            }
            catch (IllegalDataException e) {
              //noinspection HardCodedStringLiteral,UseOfSystemOutOrSystemErr
              System.out.println("Cannot save results for "
                                 + refEntity.getName());
            }
          }
        }
      }
    });
  }

  public boolean isGraphNeeded() {
    return false;
  }

  public boolean hasReportedProblems() {
    return myProblemElements.size() > 0;
  }

  public void updateContent() {
    myPackageContents = new HashMap<String, Set<RefElement>>();
    myModulesProblems = new HashSet<RefModule>();
    final Set<RefEntity> elements = myProblemElements.keySet();
    for (RefEntity element : elements) {
      if (element instanceof RefElement) {
        String packageName = RefUtil.getInstance().getPackageName(element);
        Set<RefElement> content = myPackageContents.get(packageName);
        if (content == null) {
          content = new HashSet<RefElement>();
          myPackageContents.put(packageName, content);
        }
        content.add((RefElement)element);
      } else if (element instanceof RefModule){
        myModulesProblems.add((RefModule)element);
      }
    }
  }

  public InspectionTreeNode[] getContents() {
    List<InspectionTreeNode> content = new ArrayList<InspectionTreeNode>();
    Set<String> packages = myPackageContents.keySet();
    for (String p : packages) {
      InspectionPackageNode pNode = new InspectionPackageNode(p);
      Set<RefElement> elements = myPackageContents.get(p);
      for (RefElement refElement : elements) {
        final RefElementNode elemNode = addNodeToParent(refElement, pNode);
        final CommonProblemDescriptor[] problems = myProblemElements.get(refElement);
        for (CommonProblemDescriptor problem : problems) {
          elemNode.add(new ProblemDescriptionNode(refElement, problem, !(this instanceof DuplicatePropertyInspection)));
        }
        if (problems.length == 1){
          elemNode.setProblem(problems[0]);
        }
      }
      content.add(pNode);
    }
    for (RefModule refModule : myModulesProblems) {
      InspectionModuleNode moduleNode = new InspectionModuleNode(refModule.getModule());
      final CommonProblemDescriptor[] problems = myProblemElements.get(refModule);
      for (CommonProblemDescriptor problem : problems) {
        moduleNode.add(new ProblemDescriptionNode(refModule, problem, !(this instanceof DuplicatePropertyInspection)));
      }
      content.add(moduleNode);
    }
    return content.toArray(new InspectionTreeNode[content.size()]);
  }

  public Map<String, Set<RefElement>> getPackageContent() {
    return myPackageContents;
  }

  public Set<RefModule> getModuleProblems() {
    return myModulesProblems;
  }

  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    if (refElements == null) return null;
    Map<Class, QuickFixAction> result = new java.util.HashMap<Class, QuickFixAction>();
    for (RefEntity refElement : refElements) {
      final Set<QuickFix> localQuickFixes = myQuickFixActions.get(refElement);
      if (localQuickFixes != null){
        for (QuickFix fix : localQuickFixes) {
          final Class klass = fix.getClass();
          final QuickFixAction quickFixAction = result.get(klass);
          if (quickFixAction != null){
            try {
              String familyName = fix.getFamilyName();
              familyName = familyName != null && familyName.length() > 0 ? "\'" + familyName + "\'" : familyName;
              ((LocalQuickFixWrapper)quickFixAction).setText(InspectionsBundle.message("inspection.descriptor.provider.apply.fix", familyName));
            }
            catch (AbstractMethodError e) {
              //for plugin compatibility
              ((LocalQuickFixWrapper)quickFixAction).setText(InspectionsBundle.message("inspection.descriptor.provider.apply.fix", ""));
            }
          } else {
            LocalQuickFixWrapper quickFixWrapper = new LocalQuickFixWrapper(fix, this);
            result.put(fix.getClass(), quickFixWrapper);
          }
        }
      }
    }
    return result.values().isEmpty() ? null : result.values().toArray(new QuickFixAction[result.size()]);
  }

  protected RefEntity getElement(ProblemDescriptor descriptor) {
    return myProblemToElements.get(descriptor);
  }

  public void ignoreProblem(final CommonProblemDescriptor descriptor, final QuickFix fix) {
    RefEntity refElement = myProblemToElements.get(descriptor);
    if (refElement != null) {
      final QuickFix[] fixes = descriptor.getFixes();
      for (int i = 0; i < fixes.length; i++) {
        if (fixes[i] == fix){
          ignoreProblem(refElement, descriptor, i);
          return;
        }
      }
    }
  }
}
