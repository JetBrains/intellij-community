// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell.protocol;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public class CodeSnippet implements Serializable {
  @SuppressWarnings("unused")
  public enum Status {
    VALID(true, true),
    RECOVERABLE_DEFINED(true, true),
    RECOVERABLE_NOT_DEFINED(true, false),
    DROPPED(false, false),
    OVERWRITTEN(false, false),
    REJECTED(false, false),
    NONEXISTENT(false, false),
    UNKNOWN (false, false);

    private final boolean myIsActive;
    private final boolean myIsDefined;

    Status(boolean isActive, boolean isDefined) {
      this.myIsActive = isActive;
      this.myIsDefined = isDefined;
    }

    public boolean isActive() {
      return myIsActive;
    }

    public boolean isDefined() {
      return myIsDefined;
    }
  }

  @SuppressWarnings("unused")
  public enum Kind {
    IMPORT(true),
    TYPE_DECL(true),
    METHOD(true),
    VAR(true),
    EXPRESSION(false),
    STATEMENT(false),
    ERRONEOUS(false),
    UNKNOWN(false);

    private final boolean isPersistent;

    Kind(boolean isPersistent) {
      this.isPersistent = isPersistent;
    }

    public boolean isPersistent() {
      return isPersistent;
    }
  }

  @SuppressWarnings("unused")
  public enum SubKind {
    SINGLE_TYPE_IMPORT_SUBKIND(Kind.IMPORT),
    TYPE_IMPORT_ON_DEMAND_SUBKIND(Kind.IMPORT),
    SINGLE_STATIC_IMPORT_SUBKIND(Kind.IMPORT),
    STATIC_IMPORT_ON_DEMAND_SUBKIND(Kind.IMPORT),
    CLASS_SUBKIND(Kind.TYPE_DECL),
    INTERFACE_SUBKIND(Kind.TYPE_DECL),
    ENUM_SUBKIND(Kind.TYPE_DECL),
    RECORD_SUBKIND(Kind.TYPE_DECL),
    ANNOTATION_TYPE_SUBKIND(Kind.TYPE_DECL),
    METHOD_SUBKIND(Kind.METHOD),
    VAR_DECLARATION_SUBKIND(Kind.VAR),
    VAR_DECLARATION_WITH_INITIALIZER_SUBKIND(Kind.VAR, true, true),
    TEMP_VAR_EXPRESSION_SUBKIND(Kind.VAR, true, true),
    VAR_VALUE_SUBKIND(Kind.EXPRESSION, true, true),
    ASSIGNMENT_SUBKIND(Kind.EXPRESSION, true, true),
    OTHER_EXPRESSION_SUBKIND(Kind.EXPRESSION, true, true),
    STATEMENT_SUBKIND(Kind.STATEMENT, true, false),
    UNKNOWN_SUBKIND(Kind.ERRONEOUS, false, false);

    private final boolean isExecutable;
    private final boolean hasValue;
    private final Kind kind;

    SubKind(Kind kind) {
      this.kind = kind;
      this.isExecutable = false;
      this.hasValue = false;
    }

    SubKind(Kind kind, boolean isExecutable, boolean hasValue) {
      this.kind = kind;
      this.isExecutable = isExecutable;
      this.hasValue = hasValue;
    }

    public boolean isExecutable() {
      return isExecutable;
    }

    public boolean hasValue() {
      return hasValue;
    }

    public Kind kind() {
      return kind;
    }
  }

  private String myId;
  private Kind myKind;
  private SubKind mySubKind;
  private String myCodeText;
  private String myPresentation;

  @SuppressWarnings("unused")
  public CodeSnippet() { }

  public CodeSnippet(String id, Kind kind, SubKind subKind, String codeText, String presentation) {
    myId = id;
    myKind = kind;
    mySubKind = subKind;
    myCodeText = codeText;
    myPresentation = presentation;
  }

  public String getId() {
    return myId;
  }

  public Kind getKind() {
    return myKind;
  }

  public SubKind getSubKind() {
    return mySubKind;
  }

  public String getCodeText() {
    return myCodeText;
  }

  public String getPresentation() {
    return myPresentation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CodeSnippet snippet = (CodeSnippet)o;

    if (!Objects.equals(myId, snippet.myId)) return false;
    if (myKind != snippet.myKind) return false;
    if (mySubKind != snippet.mySubKind) return false;
    if (!Objects.equals(myCodeText, snippet.myCodeText)) return false;
    if (!Objects.equals(myPresentation, snippet.myPresentation)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myId != null ? myId.hashCode() : 0;
    result = 31 * result + (myKind != null ? myKind.hashCode() : 0);
    result = 31 * result + (mySubKind != null ? mySubKind.hashCode() : 0);
    result = 31 * result + (myCodeText != null ? myCodeText.hashCode() : 0);
    result = 31 * result + (myPresentation != null ? myPresentation.hashCode() : 0);
    return result;
  }
}