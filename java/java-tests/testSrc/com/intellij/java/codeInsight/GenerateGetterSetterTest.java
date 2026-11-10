// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.codeInsight.generation.GenerateAccessorProviderRegistrar;
import com.intellij.codeInsight.generation.GenerateGetterHandler;
import com.intellij.codeInsight.generation.GenerateSetterHandler;
import com.intellij.codeInsight.generation.GetterSetterGenerationOptions;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.codeInsight.generation.SetterTemplatesManager;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.NotNullFunction;
import com.intellij.util.VisibilityUtil;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class GenerateGetterSetterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testDoNotStripIsOfNonBooleanFields() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("class YesNoRAMField {}");
    myFixture.configureByText("a.java", """
      class Foo {
          YesNoRAMField isStateForceMailField;
      
          <caret>
      }
      """);
    generateGetter();
    myFixture.checkResult("""
                            class Foo {
                                YesNoRAMField isStateForceMailField;
                            
                                public YesNoRAMField getIsStateForceMailField() {
                                    return isStateForceMailField;
                                }
                            }
                            """);
  }

  @Test
  public void testStripIsOfBooleanFields() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Foo {
          static final String CONST = "const";
          boolean isStateForceMailField;
          boolean isic;
      
          <caret>
      }
      """);
    generateGetter();
    myFixture.checkResult("""
                            class Foo {
                                static final String CONST = "const";
                                boolean isStateForceMailField;
                                boolean isic;
                            
                                public boolean isStateForceMailField() {
                                    return isStateForceMailField;
                                }
                            
                                public boolean isIsic() {
                                    return isic;
                                }
                            }
                            """);
  }

  @Test
  public void testStripIsOfBooleanFieldsSetter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class Foo {
          boolean isStateForceMailField;
      
          <caret>
      }
      """);
    generateSetter();
    myFixture.checkResult("""
                            class Foo {
                                boolean isStateForceMailField;
                            
                                public void setStateForceMailField(boolean stateForceMailField) {
                                    isStateForceMailField = stateForceMailField;
                                }
                            }
                            """);
  }

  @Test
  public void testBuilderSetterTemplate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      class X<T extends String> {
         T field;
        \s
         <caret>
      }
      """);
    try {
      SetterTemplatesManager.getInstance().getState().defaultTemplateName = "Builder";
      generateSetter();
      myFixture.checkResult("""
                              class X<T extends String> {
                                 T field;
                              
                                  public X<T> setField(T field) {
                                      this.field = field;
                                      return this;
                                  }
                              }
                              """);
    }
    finally {
      SetterTemplatesManager.getInstance().getState().defaultTemplateName = null;
    }
  }

  @Test
  public void testStripFieldPrefix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.FIELD_NAME_PREFIX = "my";
    myFixture.configureByText("a.java", """
       class Foo {
           String myName;
      
           <caret>
       }
      \s""");
    generateGetter();
    myFixture.checkResult("""
                             class Foo {
                                 String myName;
                            
                                 public String getName() {
                                     return myName;
                                 }
                             }
                            \s""");
  }

  @Test
  public void testQualifiedThis() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(UnqualifiedFieldAccessInspection.class);
    myFixture.configureByText("a.java", """
      class Foo {
          boolean isStateForceMailField;
      
          <caret>
      }
      """);
    generateGetter();
    myFixture.checkResult("""
                            class Foo {
                                boolean isStateForceMailField;
                            
                                public boolean isStateForceMailField() {
                                    return this.isStateForceMailField;
                                }
                            }
                            """);
  }

  @Test
  public void testNullableStuff() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         public @interface NotNull {}""");
    myFixture.configureByText("a.java", """
      class Foo {
          @org.jetbrains.annotations.NotNull
          private String myName;
      
          <caret>
      }
      """);
    generateGetter();
    generateSetter();
    myFixture.checkResult("""
                            import org.jetbrains.annotations.NotNull;
                            
                            class Foo {
                                @org.jetbrains.annotations.NotNull
                                private String myName;
                            
                                public void setMyName(@NotNull String myName) {
                                    this.myName = myName;
                                }
                            
                                @NotNull
                                public String getMyName() {
                                    return myName;
                                }
                            }
                            """);
  }

  @Test
  public void testNullableStuffTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;
                         
                         @Target(ElementType.TYPE_USE)
                         public @interface NotNull {}""");
    myFixture.configureByText("a.java", """
      class Foo {
          @org.jetbrains.annotations.NotNull
          private String myName;
      
          <caret>
      }
      """);
    generateGetter();
    generateSetter();
    myFixture.checkResult("""
                            import org.jetbrains.annotations.NotNull;
                            
                            class Foo {
                                @org.jetbrains.annotations.NotNull
                                private String myName;
                            
                                public void setMyName(@NotNull String myName) {
                                    this.myName = myName;
                                }
                            
                                public @NotNull String getMyName() {
                                    return myName;
                                }
                            }
                            """);
  }

  @Test
  public void testNullableStuffWithDifferentTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;
                         
                         @Target({ElementType.TYPE_USE, ElementType.ANNOTATION_TYPE})
                         public @interface NotNull {}""");
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;
                         
                         @Target({ElementType.TYPE_USE, ElementType.ANNOTATION_TYPE})
                         public @interface Size {}""");
    myFixture.configureByText("a.java", """
      import java.util.List;
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Size;
      
      class Foo {
          @NotNull
          @Size
          private String myName;
      
          private List<@NotNull @Size String> parents;
          private List<? extends @NotNull @Size String> extParents;
          private @NotNull @Size String[] children;
          <caret>
      }
      """);
    generateGetter();
    generateSetter();
    myFixture.checkResult("""
                            import java.util.List;
                            import org.jetbrains.annotations.NotNull;
                            import org.jetbrains.annotations.Size;
                            
                            class Foo {
                                @NotNull
                                @Size
                                private String myName;
                            
                                private List<@NotNull @Size String> parents;
                                private List<? extends @NotNull @Size String> extParents;
                                private @NotNull @Size String[] children;
                            
                                public void setMyName(@NotNull String myName) {
                                    this.myName = myName;
                                }
                            
                                public void setParents(List<@NotNull String> parents) {
                                    this.parents = parents;
                                }
                            
                                public void setExtParents(List<? extends @NotNull String> extParents) {
                                    this.extParents = extParents;
                                }
                            
                                public void setChildren(@NotNull String[] children) {
                                    this.children = children;
                                }
                            
                                public @NotNull String getMyName() {
                                    return myName;
                                }
                            
                                public List<@NotNull String> getParents() {
                                    return parents;
                                }
                            
                                public List<? extends @NotNull String> getExtParents() {
                                    return extParents;
                                }
                            
                                public @NotNull String[] getChildren() {
                                    return children;
                                }
                            }
                            """);
  }

  @Test
  public void testNullableStuffWithDifferentTypeUseAllAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;
                         
                         @Target(ElementType.TYPE_USE)
                         public @interface NotNull {}""");
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;
                         
                         @Target(ElementType.TYPE_USE)
                         public @interface Size {}""");
    myFixture.configureByText("a.java", """
      import java.util.List;
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Size;
      
      class Foo {
          @NotNull
          @Size
          private String myName;
      
          private List<@NotNull @Size String> parents;
          private List<? extends @NotNull @Size String> extParents;
          private @NotNull @Size String[] children;
          <caret>
      }
      """);
    generateGetter(true);
    generateSetter(true);
    myFixture.checkResult("""
                            import java.util.List;
                            import org.jetbrains.annotations.NotNull;
                            import org.jetbrains.annotations.Size;
                            
                            class Foo {
                                @NotNull
                                @Size
                                private String myName;
                            
                                private List<@NotNull @Size String> parents;
                                private List<? extends @NotNull @Size String> extParents;
                                private @NotNull @Size String[] children;
                            
                                public void setMyName(@NotNull @Size String myName) {
                                    this.myName = myName;
                                }
                            
                                public void setParents(List<@NotNull @Size String> parents) {
                                    this.parents = parents;
                                }
                            
                                public void setExtParents(List<? extends @NotNull @Size String> extParents) {
                                    this.extParents = extParents;
                                }
                            
                                public void setChildren(@NotNull @Size String[] children) {
                                    this.children = children;
                                }
                            
                                public @NotNull @Size String getMyName() {
                                    return myName;
                                }
                            
                                public List<@NotNull @Size String> getParents() {
                                    return parents;
                                }
                            
                                public List<? extends @NotNull @Size String> getExtParents() {
                                    return extParents;
                                }
                            
                                public @NotNull @Size String[] getChildren() {
                                    return children;
                                }
                            }
                            """);
  }

  @Test
  public void testPrimitivesWithTypeUseAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      import java.lang.annotation.ElementType;
      import java.lang.annotation.Target;
      
      class TestField {
          private @Anno int field<caret>;
      }
      
      @Target(ElementType.TYPE_USE)
      @interface Anno {}
      """);
    generateGetter();
    generateSetter();
    myFixture.checkResult("""
                            import java.lang.annotation.ElementType;
                            import java.lang.annotation.Target;
                            
                            class TestField {
                                public void setField(int field) {
                                    this.field = field;
                                }
                            
                                public int getField() {
                                    return field;
                                }
                            
                                private @Anno int field;
                            }
                            
                            @Target(ElementType.TYPE_USE)
                            @interface Anno {}
                            """);
  }

  @Test
  public void testLombokGeneratedFieldsWithoutContainingFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), GenerateAccessorProviderRegistrar.EP_NAME,
                                           new NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>() {
                                             @NotNull
                                             @Override
                                             public Collection<EncapsulatableClassMember> fun(PsiClass dom) {
                                               LightFieldBuilder builder =
                                                 new LightFieldBuilder(PsiManager.getInstance(myFixture.getProject()), "lombokGenerated",
                                                                       PsiTypes.intType());
                                               builder.setContainingClass(dom);
                                               return List.of(new PsiFieldMember(builder));
                                             }
                                           }, myFixture.getTestRootDisposable());
    myFixture.configureByText("a.java", """
      class A {  \s
          private String myName;
      
          <caret>
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      new GenerateGetterHandler() {
        @Override
        protected ClassMember[] chooseMembers(ClassMember[] members,
                                              boolean allowEmptySelection,
                                              boolean copyJavadocCheckbox,
                                              Project project,
                                              @Nullable Editor editor) {
          final MemberChooser<ClassMember> chooser = super.createMembersChooser(members, allowEmptySelection, copyJavadocCheckbox, project);
          Disposer.register(myFixture.getTestRootDisposable(), () -> chooser.close(DialogWrapper.OK_EXIT_CODE));
          return members;
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      myFixture.checkResult("""
                              class A {  \s
                                  private String myName;
                              
                                  public String getMyName() {
                                      return myName;
                                  }
                              
                                  public int getLombokGenerated() {
                                      return lombokGenerated;
                                  }
                              }
                              """);
    });
  }

  private void generateGetter() {
    generateGetter(false);
  }

  private void generateGetter(boolean allAnnotations) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      new GenerateGetterHandler() {
        @Override
        protected ClassMember[] chooseMembers(ClassMember[] members,
                                              boolean allowEmptySelection,
                                              boolean copyJavadocCheckbox,
                                              Project project,
                                              @Nullable Editor editor) {
          return members;
        }

        @Override
        protected @NotNull GetterSetterGenerationOptions getOptions() {
          return allAnnotations ? new GetterSetterGenerationOptions(true) : super.getOptions();
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    });
  }

  @Test
  public void testStaticOrThisSetterWithSameNameParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(UnqualifiedFieldAccessInspection.class);
    myFixture.configureByText("a.java", """
      class Foo {
          static int p;
          int f;
      
          <caret>
      }
      """);
    generateSetter();
    myFixture.checkResult("""
                            class Foo {
                                static int p;
                                int f;
                            
                                public static void setP(int p) {
                                    Foo.p = p;
                                }
                            
                                public void setF(int f) {
                                    this.f = f;
                                }
                            }
                            """);
  }

  @Test
  public void testInvokeBetweenCommentAndMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(UnqualifiedFieldAccessInspection.class);
    myFixture.configureByText("a.java", """
      class Foo {
        int a;
        //comment
       <caret> void foo() {}
      }""");
    generateGetter();
    myFixture.checkResult("""
                            class Foo {
                              int a;
                            
                                public int getA() {
                                    return this.a;
                                }
                            
                                //comment
                              void foo() {}
                            }""");
  }

  @Test
  public void testRecordAccessor() {
    doRecordAccessorTest(VisibilityUtil.ESCALATE_VISIBILITY);
    doRecordAccessorTest(PsiModifier.PRIVATE);
  }

  @Test
  public void testIncorrectParameterMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplatesManager setterTemplatesManager = SetterTemplatesManager.getInstance();
    TemplateResource template = setterTemplatesManager.getDefaultTemplate();
    Disposer.register(myFixture.getTestRootDisposable(), () -> {
      setterTemplatesManager.setDefaultTemplate(template);
    });

    myFixture.addClass(
      """
        package foo;
        
        public class CustomClass<ID, T> {
            public T getValue() {
                return null;
            }
        
            public void setValue(T value) {
        
            }
        }
        """);

    myFixture.configureByText("Sample.java",
                              """
                                package foo;
                              
                                public class Sample<ID, T> {
                                    private CustomClass<ID, T> fieldName;
                              
                                    <caret>
                                }
                                """);

    TemplateResource custom = new TemplateResource("Custom",
                                                   """
                                                     #set($paramName = $helper.getParamName($field, $project))
                                                     #if($field.modifierStatic)
                                                     static ##
                                                     #end
                                                     #if ($field.type.contains("CustomClass"))
                                                         #set($returnType = $StringUtil.trimEnd($StringUtil.substringAfter($field.type, ","), ">"))
                                                     #else
                                                         #set($returnType = $field.type)
                                                     #end
                                                     $class.name $StringUtil.sanitizeJavaIdentifier($helper.getPropertyName($field, $project))(final $returnType $paramName) {
                                                     #if ($field.name == $paramName)
                                                         #if (!$field.modifierStatic)
                                                         this.##
                                                         #else
                                                             $classname.##
                                                         #end
                                                     #end
                                                     #if ($field.type.contains("CustomClass"))
                                                         ${field.name}.setValue($paramName)##
                                                     #else
                                                         $field.name = $paramName##
                                                     #end
                                                     ;
                                                     return this;
                                                     }
                                                     """, false);
    setterTemplatesManager.addTemplate(custom);
    setterTemplatesManager.setDefaultTemplate(custom);
    EdtTestUtil.runInEdtAndWait(() -> {
      new GenerateSetterHandler() {
        @Override
        protected ClassMember[] chooseMembers(ClassMember[] members,
                                              boolean allowEmptySelection,
                                              boolean copyJavadocCheckbox,
                                              Project project,
                                              @Nullable Editor editor) {
          return members;
        }

        @Override
        protected @NotNull GetterSetterGenerationOptions getOptions() {
          return super.getOptions();
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      myFixture.checkResult(
                              """
                                package foo;
                              
                                public class Sample<ID, T> {
                                    private CustomClass<ID, T> fieldName;
                              
                                    public Sample fieldName(T fieldName) {
                                        this.fieldName.setValue(fieldName);
                                        return this;
                                    }
                                }
                                """);
    });
  }

  private void doRecordAccessorTest(String visibility) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).VISIBILITY = visibility;
    myFixture.configureByText("a.java", """
      record Point(int x, int y) {
        <caret>
      }
      """);
    generateGetter();
    myFixture.checkResult("""
                            record Point(int x, int y) {
                                @Override
                                public int x() {
                                    return x;
                                }
                            
                                @Override
                                public int y() {
                                    return y;
                                }
                            }
                            """);
  }

  private void generateSetter() {
    generateSetter(false);
  }

  private void generateSetter(boolean allAnnotations) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      new GenerateSetterHandler() {
        @Override
        protected ClassMember[] chooseMembers(ClassMember[] members,
                                              boolean allowEmptySelection,
                                              boolean copyJavadocCheckbox,
                                              Project project,
                                              @Nullable Editor editor) {
          return members;
        }

        @Override
        protected @NotNull GetterSetterGenerationOptions getOptions() {
          return allAnnotations ? new GetterSetterGenerationOptions(true) : super.getOptions();
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    });
  }
}
