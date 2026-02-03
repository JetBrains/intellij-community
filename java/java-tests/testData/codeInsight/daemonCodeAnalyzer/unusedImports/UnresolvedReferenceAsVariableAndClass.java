import com.<error descr="Cannot resolve symbol 'intellij'">intellij</error>.lang.javascript.JavaScriptFileType;
import com.<error descr="Cannot resolve symbol 'intellij'">intellij</error>.template.lang.core.templateLanguages.TemplatesService;

public class UnresolvedReferenceAsVariableAndClass {
  public void setUp() {
    Object <warning descr="Variable 'TemplatesService' is never assigned">TemplatesService</warning>;
    final <error descr="Unknown class: 'TemplatesService'">TemplatesService</error> <warning descr="Variable 'service' is never used">service</warning> = <error descr="Variable 'TemplatesService' might not have been initialized">TemplatesService</error>.getInstance();
    System.out.println(<error descr="Cannot resolve symbol 'JavaScriptFileType'">JavaScriptFileType</error>.INSTANCE);
  }
}
