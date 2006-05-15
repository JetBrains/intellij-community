/**
 * created at Jan 3, 2002
 * @author Jeka
 */
package com.intellij.compiler;

import com.intellij.CommonBundle;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler;
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseEmbeddedCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacEmbeddedCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.compiler.impl.javaCompiler.jikes.JikesCompiler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;import com.intellij.openapi.fileChooser.FileChooserDescriptor;import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.util.Options;
import org.apache.oro.text.regex.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class CompilerConfiguration implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.CompilerConfiguration");
  public static final @NonNls String TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME = "tests.external.compiler.home";
  public static final int DEPENDENCY_FORMAT_VERSION = 44;
  @NonNls private static final String PROPERTY_IDEA_USE_EMBEDDED_JAVAC = "idea.use.embedded.javac";

  public String DEFAULT_COMPILER;
  @NotNull private BackendCompiler myDefaultJavaCompiler;

  // extensions of the files considered as resource files
  private List<Pattern> myRegexpResourcePaterns = new ArrayList<Pattern>(getDefaultRegexpPatterns());
  // extensions of the files considered as resource files. If present, Overrides patterns in old regexp format stored in myRegexpResourcePaterns
  private List<String> myWildcardPatterns = new ArrayList<String>();
  private List<Pattern> myWildcardCompiledPatterns = new ArrayList<Pattern>();
  private boolean myWildcardPatternsInitialized = false;
  private final Project myProject;
  private final ExcludedEntriesConfiguration myExcludedEntriesConfiguration;


  public int DEPLOY_AFTER_MAKE = Options.SHOW_DIALOG;

  private final Collection<BackendCompiler> registeredCompilers = new ArrayList<BackendCompiler>();
  private BackendCompiler JAVAC_EXTERNAL_BACKEND;
  private BackendCompiler JAVAC_EMBEDDED_BACKEND;
  private BackendCompiler JIKES_BACKEND;
  private BackendCompiler ECLIPSE_BACKEND;
  private BackendCompiler ECLIPSE_EMBEDDED_BACKEND;
  private final Perl5Matcher myPatternMatcher = new Perl5Matcher();

  {
    loadDefaultWildcardPatterns();
  }

  public CompilerConfiguration(Project project) {
    myProject = project;
    myExcludedEntriesConfiguration = new ExcludedEntriesConfiguration(new Factory<FileChooserDescriptor>() {
      public FileChooserDescriptor create() {
        final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true);
        final VirtualFile[] roots = ProjectRootManager.getInstance(myProject).getContentSourceRoots();
        for (VirtualFile contentSourceRoot : roots) {
          descriptor.addRoot(contentSourceRoot);
        }
        return descriptor;
      }
    });
  }

  private void loadDefaultWildcardPatterns() {
    if (!myWildcardPatterns.isEmpty()) {
      removeWildcardPatterns();
    }
    try {
      addWildcardResourcePattern("?*.properties");
      addWildcardResourcePattern("?*.xml");
      addWildcardResourcePattern("?*.gif");
      addWildcardResourcePattern("?*.png");
      addWildcardResourcePattern("?*.jpeg");
      addWildcardResourcePattern("?*.jpg");
      addWildcardResourcePattern("?*.html");
      addWildcardResourcePattern("?*.dtd");
      addWildcardResourcePattern("?*.tld");
    }
    catch (MalformedPatternException e) {
      LOG.error(e);
    }
  }

  private static List<Pattern> getDefaultRegexpPatterns() {
    try {
      return Arrays.asList(compilePattern(".+\\.(properties|xml|html|dtd|tld)"), compilePattern(".+\\.(gif|png|jpeg|jpg)"));
    }
    catch (MalformedPatternException e) {
      LOG.error(e);
    }
    return Collections.emptyList();
  }

  public static String getTestsExternalCompilerHome() {
    String compilerHome = System.getProperty(TESTS_EXTERNAL_COMPILER_HOME_PROPERTY_NAME, null);
    if (compilerHome == null) {
      compilerHome = new File(System.getProperty("java.home")).getParentFile().getAbsolutePath();
    }
    return compilerHome;
  }

  private static Pattern compilePattern(@NonNls String s) throws MalformedPatternException {
    final PatternCompiler compiler = new Perl5Compiler();
    final Pattern pattern;
    if (SystemInfo.isFileSystemCaseSensitive) {
      pattern = compiler.compile(s);
    }
    else {
      pattern = compiler.compile(s, Perl5Compiler.CASE_INSENSITIVE_MASK);
    }
    return pattern;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public BackendCompiler getJavacCompiler() {
    return JAVAC_EXTERNAL_BACKEND;
  }

  public void projectOpened() {
    createCompilers();
  }

  private void createCompilers() {
    JAVAC_EXTERNAL_BACKEND = new JavacCompiler(myProject);
    registeredCompilers.add(JAVAC_EXTERNAL_BACKEND);
    JAVAC_EMBEDDED_BACKEND = new JavacEmbeddedCompiler(myProject);
    //registeredCompilers.add(JAVAC_EMBEDDED_BACKEND);

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      JIKES_BACKEND = new JikesCompiler(myProject);
      ECLIPSE_BACKEND = new EclipseCompiler(myProject);
      ECLIPSE_EMBEDDED_BACKEND = new EclipseEmbeddedCompiler(myProject);

      registeredCompilers.add(JIKES_BACKEND);
      registeredCompilers.add(ECLIPSE_BACKEND);
      registeredCompilers.add(ECLIPSE_EMBEDDED_BACKEND);
    }
    myDefaultJavaCompiler = JAVAC_EXTERNAL_BACKEND;
    for (BackendCompiler compiler : registeredCompilers) {
      if (compiler.getId().equals(DEFAULT_COMPILER)) {
        myDefaultJavaCompiler = compiler;
        break;
      }
    }
    DEFAULT_COMPILER = myDefaultJavaCompiler.getId();
  }

  public Collection<BackendCompiler> getRegisteredJavaCompilers() {
    return registeredCompilers;
  }

  public String[] getResourceFilePatterns() {
    return getWildcardPatterns();
  }

  public String[] getRegexpPatterns() {
    String[] patterns = new String[myRegexpResourcePaterns.size()];
    int index = 0;
    for (final Pattern myRegexpResourcePatern : myRegexpResourcePaterns) {
      patterns[index++] = myRegexpResourcePatern.getPattern();
    }
    return patterns;
  }

  public String[] getWildcardPatterns() {
    return myWildcardPatterns.toArray(new String[myWildcardPatterns.size()]);
  }

  public void addResourceFilePattern(String namePattern) throws MalformedPatternException {
    addWildcardResourcePattern(namePattern);
  }

  // need this method only for handling patterns in old regexp format
  private void addRegexpPattern(String namePattern) throws MalformedPatternException {
    Pattern pattern = compilePattern(namePattern);
    if (pattern != null) {
      myRegexpResourcePaterns.add(pattern);
    }
  }

  public ExcludedEntriesConfiguration getExcludedEntriesConfiguration() {
    return myExcludedEntriesConfiguration;
  }

  public boolean isExcludedFromCompilation(final VirtualFile virtualFile) {
    return myExcludedEntriesConfiguration.isExcluded(virtualFile);
  }

  private void addWildcardResourcePattern(@NonNls final String wildcardPattern) throws MalformedPatternException {
    final Pattern pattern = compilePattern(convertToRegexp(wildcardPattern));
    if (pattern != null) {
      myWildcardPatterns.add(wildcardPattern);
      myWildcardCompiledPatterns.add(pattern);
    }
  }

  public void removeResourceFilePatterns() {
    removeWildcardPatterns();
  }

  public void removeRegexpPatterns() {
    myRegexpResourcePaterns.clear();
  }

  public void removeWildcardPatterns() {
    myWildcardPatterns.clear();
    myWildcardCompiledPatterns.clear();
  }

  private static String convertToRegexp(String wildcardPattern) {
    if (isPatternNegated(wildcardPattern)) {
      wildcardPattern = wildcardPattern.substring(1);
    }
    return wildcardPattern.
      replaceAll("\\\\!", "!").
      replaceAll("\\.", "\\\\.").
      replaceAll("\\*\\?", ".+").
      replaceAll("\\?\\*", ".+").
      replaceAll("\\*", ".*").
      replaceAll("\\?", ".").
      replaceAll("(?:\\.\\*)+", ".*")  // optimization
    ;
  }

  public static boolean isPatternNegated(String wildcardPattern) {
    return wildcardPattern.length() > 1 && wildcardPattern.charAt(0) == '!';
  }

  public boolean isResourceFile(String name) {
    for (int i = 0; i < myWildcardCompiledPatterns.size(); i++) {
      Pattern pattern = myWildcardCompiledPatterns.get(i);
      final String wildcard = myWildcardPatterns.get(i);
      final boolean matches = myPatternMatcher.matches(name, pattern);
      if (isPatternNegated(wildcard) ? !matches : matches) {
        return true;
      }
    }
    return false;
  }

  // property names
  private static final @NonNls String EXCLUDE_FROM_COMPILE = "excludeFromCompile";
  private static final @NonNls String RESOURCE_EXTENSIONS = "resourceExtensions";
  private static final @NonNls String WILDCARD_RESOURCE_PATTERNS = "wildcardResourcePatterns";
  private static final @NonNls String ENTRY = "entry";
  private static final @NonNls String NAME = "name";

  public void readExternal(Element parentNode) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, parentNode);

    Element node = parentNode.getChild(EXCLUDE_FROM_COMPILE);
    if (node != null) {
      myExcludedEntriesConfiguration.readExternal(node);
    }

    try {
      removeRegexpPatterns();
      node = parentNode.getChild(RESOURCE_EXTENSIONS);
      if (node != null) {
        for (final Object o : node.getChildren(ENTRY)) {
          Element element = (Element)o;
          String pattern = element.getAttributeValue(NAME);
          if (pattern != null && !"".equals(pattern)) {
            addRegexpPattern(pattern);
          }
        }
      }

      removeWildcardPatterns();
      node = parentNode.getChild(WILDCARD_RESOURCE_PATTERNS);
      if (node != null) {
        myWildcardPatternsInitialized = true;
        for (final Object o : node.getChildren(ENTRY)) {
          final Element element = (Element)o;
          String pattern = element.getAttributeValue(NAME);
          if (pattern != null && !"".equals(pattern)) {
            addWildcardResourcePattern(pattern);
          }
        }
      }
    }
    catch (MalformedPatternException e) {
      throw new InvalidDataException(e);
    }

  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, parentNode);

    if(myExcludedEntriesConfiguration.getExcludeEntryDescriptions().length > 0) {
      Element newChild = new Element(EXCLUDE_FROM_COMPILE);
      myExcludedEntriesConfiguration.writeExternal(newChild);
      parentNode.addContent(newChild);
    }

    String[] patterns = getRegexpPatterns();
    final Element newChild = new Element(RESOURCE_EXTENSIONS);
    for (final String pattern : patterns) {
      final Element entry = new Element(ENTRY);
      entry.setAttribute(NAME, pattern);
      newChild.addContent(entry);
    }
    parentNode.addContent(newChild);

    if (myWildcardPatternsInitialized || !myWildcardPatterns.isEmpty()) {
      final Element wildcardPatterns = new Element(WILDCARD_RESOURCE_PATTERNS);
      for (final String wildcardPattern : myWildcardPatterns) {
        final Element entry = new Element(ENTRY);
        entry.setAttribute(NAME, wildcardPattern);
        wildcardPatterns.addContent(entry);
      }
      parentNode.addContent(wildcardPatterns);
    }
  }

  public static CompilerConfiguration getInstance(Project project) {
    return project.getComponent(CompilerConfiguration.class);
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "CompilerConfiguration";
  }

  public BackendCompiler getDefaultCompiler() {
    if (JAVAC_EXTERNAL_BACKEND == null) {
      createCompilers();
    }
    if (myDefaultJavaCompiler != JAVAC_EXTERNAL_BACKEND) return myDefaultJavaCompiler;
    boolean runEmbedded = ApplicationManager.getApplication().isUnitTestMode()
                          ? !JavacSettings.getInstance(myProject).isTestsUseExternalCompiler()
                          : Boolean.parseBoolean(System.getProperty(PROPERTY_IDEA_USE_EMBEDDED_JAVAC));
    return runEmbedded ? JAVAC_EMBEDDED_BACKEND : JAVAC_EXTERNAL_BACKEND;
  }

  public void setDefaultCompiler(BackendCompiler defaultCompiler) {
    myDefaultJavaCompiler = defaultCompiler;
    DEFAULT_COMPILER = defaultCompiler.getId();
  }

  public void convertPatterns() {
    if (!needPatternConversion()) {
      return;
    }
    try {
      boolean ok;
      try {
        ok = doConvertPatterns();
      }
      catch (MalformedPatternException e) {
        ok = false;
      }
      if (!ok) {
        final String initialPatternString = patternsToString(getRegexpPatterns());
        final String message = CompilerBundle.message(
          "message.resource.patterns.format.changed",
          ApplicationNamesInfo.getInstance().getProductName(),
          initialPatternString,
          CommonBundle.getOkButtonText(),
          CommonBundle.getCancelButtonText()
        );
        final String wildcardPatterns = Messages.showInputDialog(
          myProject, message, CompilerBundle.message("pattern.conversion.dialog.title"), Messages.getWarningIcon(), initialPatternString, new InputValidator() {
          public boolean checkInput(String inputString) {
            return true;
          }
          public boolean canClose(String inputString) {
            final StringTokenizer tokenizer = new StringTokenizer(inputString, ";", false);
            StringBuffer malformedPatterns = new StringBuffer();

            while (tokenizer.hasMoreTokens()) {
              String pattern = tokenizer.nextToken();
              try {
                addWildcardResourcePattern(pattern);
              }
              catch (MalformedPatternException e) {
                malformedPatterns.append("\n\n");
                malformedPatterns.append(pattern);
                malformedPatterns.append(": ");
                malformedPatterns.append(e.getMessage());
              }
            }

            if (malformedPatterns.length() > 0) {
              Messages.showErrorDialog(CompilerBundle.message("error.bad.resource.patterns", malformedPatterns.toString()),
                                       CompilerBundle.message("bad.resource.patterns.dialog.title"));
              removeWildcardPatterns();
              return false;
            }
            return true;
          }
        });
        if (wildcardPatterns == null) { // cancel pressed
          loadDefaultWildcardPatterns();
        }
      }
    }
    finally {
      myWildcardPatternsInitialized = true;
    }
  }

  private boolean needPatternConversion() {
    return !myWildcardPatternsInitialized && !myRegexpResourcePaterns.isEmpty();
  }

  private boolean doConvertPatterns() throws MalformedPatternException {
    final String[] regexpPatterns = getRegexpPatterns();
    final List<String> converted = new ArrayList<String>();
    final Pattern multipleExtensionsPatternPattern = compilePattern("\\.\\+\\\\\\.\\((\\w+(?:\\|\\w+)*)\\)");
    final Pattern singleExtensionPatternPattern = compilePattern("\\.\\+\\\\\\.(\\w+)");
    final Perl5Matcher matcher = new Perl5Matcher();
    for (final String regexpPattern : regexpPatterns) {
      //final Matcher multipleExtensionsMatcher = multipleExtensionsPatternPattern.matcher(regexpPattern);
      if (matcher.matches(regexpPattern, multipleExtensionsPatternPattern)) {
        final MatchResult match = matcher.getMatch();
        final StringTokenizer tokenizer = new StringTokenizer(match.group(1), "|", false);
        while (tokenizer.hasMoreTokens()) {
          converted.add("?*." + tokenizer.nextToken());
        }
      }
      else {
        //final Matcher singleExtensionMatcher = singleExtensionPatternPattern.matcher(regexpPattern);
        if (matcher.matches(regexpPattern, singleExtensionPatternPattern)) {
          final MatchResult match = matcher.getMatch();
          converted.add("?*." + match.group(1));
        }
        else {
          return false;
        }
      }
    }
    for (final String aConverted : converted) {
      addWildcardResourcePattern(aConverted);
    }
    return true;
  }

  private static String patternsToString(final String[] patterns) {
    final StringBuffer extensionsString = new StringBuffer();
    for (int idx = 0; idx < patterns.length; idx++) {
      if (idx > 0) {
        extensionsString.append(";");
      }
      extensionsString.append(patterns[idx]);
    }
    return extensionsString.toString();
  }

}
