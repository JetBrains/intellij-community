package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jdom.IllegalDataException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author max
 */
public abstract class DescriptorProviderInspection extends InspectionTool implements ProblemDescriptionsProcessor {
  private Map<RefEntity, CommonProblemDescriptor[]> myProblemElements;
  private HashMap<String, Set<RefEntity>> myPackageContents = null;
  private HashSet<RefModule> myModulesProblems = null;
  private Map<CommonProblemDescriptor, RefEntity> myProblemToElements;
  private DescriptorComposer myComposer;
  private Map<RefEntity, Set<QuickFix>> myQuickFixActions;
  private Map<RefEntity, CommonProblemDescriptor[]> myIgnoredElements;

  private HashMap<RefEntity, CommonProblemDescriptor[]> myOldProblemElements = null;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.DescriptorProviderInspection");

  public void addProblemElement(RefEntity refElement, CommonProblemDescriptor... descriptions){
    addProblemElement(refElement, true, descriptions);
  }

  protected void addProblemElement(RefEntity refElement, boolean filterSuppresssed, CommonProblemDescriptor... descriptions) {
    if (refElement == null) return;
    if (descriptions == null || descriptions.length == 0) return;
    if (filterSuppresssed) {
      if (ourOutputPath == null || !(this instanceof LocalInspectionToolWrapper)) {
        CommonProblemDescriptor[] problems = getProblemElements().get(refElement);
        if (problems == null) {
          problems = descriptions;
        }
        else {
          problems = ArrayUtil.mergeArrays(problems, descriptions, CommonProblemDescriptor.class);
        }
        getProblemElements().put(refElement, problems);
        for (CommonProblemDescriptor description : descriptions) {
          getProblemToElements().put(description, refElement);
          collectQuickFixes(description.getFixes(), refElement);
        }
      }
      else {
        writeOutput(descriptions, refElement);
      }
    }
    else { //just need to collect problems
      for (CommonProblemDescriptor description : descriptions) {
        getProblemToElements().put(description, refElement);
      }
    }
  }

  private void writeOutput(final CommonProblemDescriptor[] descriptions, final RefEntity refElement) {
    final Element parentNode = new Element(InspectionsBundle.message("inspection.problems"));
    exportResults(descriptions, refElement, parentNode);
    final List list = parentNode.getChildren();

    @NonNls final String ext = ".xml";
    final String fileName = ourOutputPath + File.separator + getShortName() + ext;
    final PathMacroManager pathMacroManager = PathMacroManager.getInstance(getContext().getProject());
    PrintWriter printWriter = null;
    try {
      new File(ourOutputPath).mkdirs();
      final File file = new File(fileName);
      final CharArrayWriter writer = new CharArrayWriter();
      for (Object o : list) {
        final Element element = (Element)o;
        pathMacroManager.collapsePaths(element);
        JDOMUtil.writeElement(element, writer, "\n");
      }
      printWriter = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
      printWriter.append("\n");
      printWriter.append(writer.toString());
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      if (printWriter != null) {
        printWriter.close();
      }
    }
  }

  public Collection<CommonProblemDescriptor> getProblemDescriptors() {
    return getProblemToElements().keySet();
  }

  private void collectQuickFixes(final QuickFix[] fixes, final RefEntity refEntity) {
    if (fixes != null) {
      Set<QuickFix> localQuickFixes = getQuickFixActions().get(refEntity);
      if (localQuickFixes == null) {
        localQuickFixes = new HashSet<QuickFix>();
        getQuickFixActions().put(refEntity, localQuickFixes);
      }
      localQuickFixes.addAll(Arrays.asList(fixes));
    }
  }

  public void ignoreElement(final RefEntity refEntity) {
    if (refEntity == null) return;
    getProblemElements().remove(refEntity);
    getQuickFixActions().remove(refEntity);
  }

  public void ignoreCurrentElement(RefEntity refEntity) {
    if (refEntity == null) return;
    getIgnoredElements().put(refEntity, getProblemElements().get(refEntity));
  }

  public void amnesty(RefEntity refEntity) {
    getIgnoredElements().remove(refEntity);
  }

  public void ignoreProblem(RefEntity refEntity, CommonProblemDescriptor problem, int idx) {
    if (refEntity == null) return;
    final Set<QuickFix> localQuickFixes = getQuickFixActions().get(refEntity);
    final QuickFix[] fixes = problem.getFixes();
    if (isIgnoreProblem(fixes, localQuickFixes, idx)){
      getProblemToElements().remove(problem);
      CommonProblemDescriptor[] descriptors = getProblemElements().get(refEntity);
      if (descriptors != null) {
        ArrayList<CommonProblemDescriptor> newDescriptors = new ArrayList<CommonProblemDescriptor>(Arrays.asList(descriptors));
        newDescriptors.remove(problem);
        getQuickFixActions().put(refEntity, null);
        if (!newDescriptors.isEmpty()) {
          getProblemElements().put(refEntity, newDescriptors.toArray(new ProblemDescriptor[newDescriptors.size()]));
          for (CommonProblemDescriptor descriptor : newDescriptors) {
            collectQuickFixes(descriptor.getFixes(), refEntity);
          }
        }
        else {
          ignoreProblemElement(refEntity);
        }
      }
    }
  }

  private void ignoreProblemElement(RefEntity refEntity){
    final CommonProblemDescriptor[] problemDescriptors = getProblemElements().remove(refEntity);
    getIgnoredElements().put(refEntity, problemDescriptors);
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
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldProblemElements == null) {
        myOldProblemElements = new HashMap<RefEntity, CommonProblemDescriptor[]>();
      }
      myOldProblemElements.clear();
      myOldProblemElements.putAll(getIgnoredElements());
      myOldProblemElements.putAll(getProblemElements());
    } else {
      myOldProblemElements = null;
    }

    myProblemElements = null;
    myProblemToElements = null;
    myQuickFixActions = null;
    myIgnoredElements = null;
    myPackageContents = null;
    myModulesProblems = null;
  }


  public void finalCleanup() {
    super.finalCleanup();
    myOldProblemElements = null;
  }

  @Nullable
  public CommonProblemDescriptor[] getDescriptions(RefEntity refEntity) {
    if (!refEntity.isValid()) {
      ignoreElement(refEntity);
      return null;
    }

    return getProblemElements().get(refEntity);
  }

  public HTMLComposerImpl getComposer() {
    if (myComposer == null) {
      myComposer = new DescriptorComposer(this);
    }
    return myComposer;
  }

  public void exportResults(final Element parentNode) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(final RefEntity refEntity) {
        if (getProblemElements().containsKey(refEntity)) {
          CommonProblemDescriptor[] descriptions = getDescriptions(refEntity);
          exportResults(descriptions, refEntity, parentNode);
        }
      }
    });
  }

  private void exportResults(final CommonProblemDescriptor[] descriptions, final RefEntity refEntity, final Element parentNode) {
    for (CommonProblemDescriptor description : descriptions) {
      @NonNls final String template = description.getDescriptionTemplate();
      int line = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getLineNumber() : -1;
      final String text = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getPsiElement().getText() : "";
      @NonNls String problemText = template.replaceAll("#ref", text.replaceAll("\\$", "\\\\\\$"));
      problemText = problemText.replaceAll(" #loc ", " ");

      Element element = XMLExportUtl.createElement(refEntity, parentNode, line, description instanceof ProblemDescriptorImpl
                                                                                ? ((ProblemDescriptorImpl)description).getTextRange() : null);
      @NonNls Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));
      problemClassElement.addContent(getDisplayName());
      if (refEntity instanceof RefElement){
        final RefElement refElement = (RefElement)refEntity;
        final HighlightSeverity severity = getCurrentSeverity(refElement);
        ProblemHighlightType problemHighlightType = description instanceof ProblemDescriptor
                                                    ? ((ProblemDescriptor)description).getHighlightType()
                                                    : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        final String attributeKey = getTextAttributeKey(refElement.getElement().getProject(), severity, problemHighlightType);
        problemClassElement.setAttribute("severity", severity.myName);
        problemClassElement.setAttribute("attribute_key", attributeKey);
      }
      element.addContent(problemClassElement);
      if (this instanceof GlobalInspectionToolWrapper) {
        final GlobalInspectionTool globalInspectionTool = ((GlobalInspectionToolWrapper)this).getTool();
        final QuickFix[] fixes = description.getFixes();
        if (fixes != null) {
          @NonNls Element hintsElement = new Element("hints");
          for (QuickFix fix : fixes) {
            final String hint = globalInspectionTool.getHint(fix);
            if (hint != null) {
              @NonNls Element hintElement = new Element("hint");
              hintElement.setAttribute("value", hint);
              hintsElement.addContent(hintElement);
            }
          }
          element.addContent(hintsElement);
        }
      }
      try {
        Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
        descriptionElement.addContent(problemText);
        element.addContent(descriptionElement);
      }
      catch (IllegalDataException e) {
        //noinspection HardCodedStringLiteral,UseOfSystemOutOrSystemErr
        System.out.println("Cannot save results for " + refEntity.getName() + ", inspection which caused problem: " + getShortName());
      }
    }
  }

  public boolean isGraphNeeded() {
    return false;
  }

  public boolean hasReportedProblems() {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_ONLY_DIFF) {
      for (CommonProblemDescriptor descriptor : getProblemToElements().keySet()) {
        if (getProblemStatus(descriptor) != FileStatus.NOT_CHANGED) {
          return true;
        }
      }
      if (myOldProblemElements != null) {
        for (RefEntity entity : myOldProblemElements.keySet()) {
          if (getElementStatus(entity) != FileStatus.NOT_CHANGED) {
            return true;
          }
        }
      }
      return false;
    }
    if (!getProblemElements().isEmpty()) return true;
    return context != null &&
           context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN &&
           myOldProblemElements != null && !myOldProblemElements.isEmpty();
  }

  public void updateContent() {
    myPackageContents = new HashMap<String, Set<RefEntity>>();
    myModulesProblems = new HashSet<RefModule>();
    final Set<RefEntity> elements = getProblemElements().keySet();
    for (RefEntity element : elements) {
      if (getContext().getUIOptions().FILTER_RESOLVED_ITEMS && getIgnoredElements().containsKey(element)) continue;
      if (element instanceof RefModule) {
        myModulesProblems.add((RefModule)element);
      }
      else {
        String packageName = RefUtil.getInstance().getPackageName(element);
        Set<RefEntity> content = myPackageContents.get(packageName);
        if (content == null) {
          content = new HashSet<RefEntity>();
          myPackageContents.put(packageName, content);
        }
        content.add(element);
      }
    }
  }

  public Map<String, Set<RefEntity>> getPackageContent() {
    return myPackageContents;
  }

  public Map<String, Set<RefEntity>> getOldPackageContent() {
    if (myOldProblemElements == null) return null;
    final HashMap<String, Set<RefEntity>> oldContents = new HashMap<String, Set<RefEntity>>();
    final Set<RefEntity> elements = myOldProblemElements.keySet();
    for (RefEntity element : elements) {
      String packageName = RefUtil.getInstance().getPackageName(element);
      final Set<RefEntity> collection = myPackageContents.get(packageName);
      if (collection != null) {
        final Set<RefEntity> currentElements = new HashSet<RefEntity>(collection);
        if (contains(element, currentElements)) continue;
      }
      Set<RefEntity> oldContent = oldContents.get(packageName);
      if (oldContent == null) {
        oldContent = new HashSet<RefEntity>();
        oldContents.put(packageName, oldContent);
      }
      oldContent.add(element);
    }
    return oldContents;
  }

  public Set<RefModule> getModuleProblems() {
    return myModulesProblems;
  }

  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    return extractActiveFixes(refElements, getQuickFixActions());    
  }

  public QuickFixAction[] extractActiveFixes(final RefEntity[] refElements, final Map<RefEntity, Set<QuickFix>> actions) {
    if (refElements == null) return null;
    Map<Class, QuickFixAction> result = new java.util.HashMap<Class, QuickFixAction>();
    for (RefEntity refElement : refElements) {
      final Set<QuickFix> localQuickFixes = actions.get(refElement);
      if (localQuickFixes != null){
        for (QuickFix fix : localQuickFixes) {
          if (fix == null) continue;
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
            result.put(klass, quickFixWrapper);
          }
        }
      }
    }
    return result.values().isEmpty() ? null : result.values().toArray(new QuickFixAction[result.size()]);
  }

  public RefEntity getElement(CommonProblemDescriptor descriptor) {
    return getProblemToElements().get(descriptor);
  }

  public void ignoreProblem(final CommonProblemDescriptor descriptor, final QuickFix fix) {
    RefEntity refElement = getProblemToElements().get(descriptor);
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


  public boolean isElementIgnored(final RefEntity element) {
    if (getIgnoredElements() == null) return false;
    for (RefEntity entity : getIgnoredElements().keySet()) {
      if (Comparing.equal(entity, element)) {
        return true;
      }
    }
    return false;
  }

  public FileStatus getProblemStatus(final CommonProblemDescriptor descriptor) {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldProblemElements != null){
        final Set<CommonProblemDescriptor> allAvailable = new HashSet<CommonProblemDescriptor>();
        for (CommonProblemDescriptor[] descriptors : myOldProblemElements.values()) {
          if (descriptors != null) {
            allAvailable.addAll(Arrays.asList(descriptors));
          }
        }
        final boolean old = contains(descriptor, allAvailable);
        final boolean current = contains(descriptor, getProblemToElements().keySet());
        return calcStatus(old, current);
      }
    }
    return FileStatus.NOT_CHANGED;
  }

  private static boolean contains(CommonProblemDescriptor descriptor, Collection<CommonProblemDescriptor> descriptors){
    PsiElement element = null;
    if (descriptor instanceof ProblemDescriptor){
      element = ((ProblemDescriptor)descriptor).getPsiElement();
    }
    for (CommonProblemDescriptor problemDescriptor : descriptors) {
      if (problemDescriptor instanceof ProblemDescriptor){
        if (!Comparing.equal(element, ((ProblemDescriptor)problemDescriptor).getPsiElement())){
          continue;
        }
      }
      if (Comparing.strEqual(problemDescriptor.getDescriptionTemplate(), descriptor.getDescriptionTemplate())){
        return true;
      }
    }
    return false;
  }


  public FileStatus getElementStatus(final RefEntity element) {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldProblemElements != null){
        final boolean old = contains(element, myOldProblemElements.keySet());
        final boolean current = contains(element, getProblemElements().keySet());
        return calcStatus(old, current);
      }
    }
    return FileStatus.NOT_CHANGED;
  }

  public Collection<RefEntity> getIgnoredRefElements() {
    return getIgnoredElements().keySet();
  }

  public Map<RefEntity, CommonProblemDescriptor[]> getProblemElements() {
    if (myProblemElements == null) {
      myProblemElements = Collections.synchronizedMap(new THashMap<RefEntity, CommonProblemDescriptor[]>());
    }
    return myProblemElements;
  }

  @Nullable
  public HashMap<RefEntity, CommonProblemDescriptor[]> getOldProblemElements() {
    return myOldProblemElements;
  }

  private Map<CommonProblemDescriptor, RefEntity> getProblemToElements() {
    if (myProblemToElements == null) {
      myProblemToElements = Collections.synchronizedMap(new THashMap<CommonProblemDescriptor, RefEntity>());
    }
    return myProblemToElements;
  }

  private Map<RefEntity, Set<QuickFix>> getQuickFixActions() {
    if (myQuickFixActions == null) {
      myQuickFixActions = Collections.synchronizedMap(new HashMap<RefEntity, Set<QuickFix>>());
    }
    return myQuickFixActions;
  }

  private Map<RefEntity, CommonProblemDescriptor[]> getIgnoredElements() {
    if (myIgnoredElements == null) {
      myIgnoredElements = Collections.synchronizedMap(new HashMap<RefEntity, CommonProblemDescriptor[]>());
    }
    return myIgnoredElements;
  }
}
