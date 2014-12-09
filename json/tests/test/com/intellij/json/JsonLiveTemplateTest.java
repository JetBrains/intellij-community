package com.intellij.json;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.json.liveTemplates.JsonContextType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonLiveTemplateTest extends JsonTestCase {

  private void doExpandabilityTest(@NotNull String originalText, boolean canBeExpanded) {
    myFixture.configureByText(JsonFileType.INSTANCE, originalText);
    final Template template = createJsonTemplate("foo", "foo", "[42]");
    assertEquals(canBeExpanded, TemplateManagerImpl.isApplicable(myFixture.getFile(), myFixture.getCaretOffset(), (TemplateImpl)template));
  }

  @NotNull
  private Template createJsonTemplate(@NotNull String name, @NotNull String group, @NotNull String text) {
    final TemplateManager templateManager = TemplateManager.getInstance(getProject());
    final Template template = templateManager.createTemplate(name, group, text);

    final TemplateContextType context = ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), JsonContextType.class);
    assertNotNull(context);
    ((TemplateImpl)template).getTemplateContext().setEnabled(context, true);
    return template;
  }

  public void testNotExpandableInsideStringLiteral() {
    doExpandabilityTest("{\"bar\": \"fo<caret>o\"}", false);
  }

  public void testNotExpandableInsidePropertyKey() {
    doExpandabilityTest("{fo<caret>o: \"bar\"}", false);
  }

  public void testExpandableAtTopLevel() {
    doExpandabilityTest("fo<caret>o", true);
  }
}
