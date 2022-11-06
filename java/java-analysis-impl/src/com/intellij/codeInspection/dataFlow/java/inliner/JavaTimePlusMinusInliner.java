// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inliner;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.java.CFGBuilder;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

/**
 * An inliner which is capable to inline minus... or plus... for LocalDate, LocalTime, LocalDateTime, OffsetTime, OffsetDateTime
 * to add contract between result and qualifier
 * and check ChronoUnit enum for minus(long, java.time.temporal.TemporalUnit) and plus(long, java.time.temporal.TemporalUnit)
 */
public class JavaTimePlusMinusInliner implements CallInliner {

  private static final CallMatcher PLUS_MINUS_TO_JAVA_TIME = anyOf(
    instanceCall(JAVA_TIME_LOCAL_DATE, "plusYears", "plusMonths", "plusWeeks", "plusDays",
                 "minusYears", "minusMonths", "minusWeeks", "minusDays").parameterTypes("long"),
    instanceCall(JAVA_TIME_LOCAL_DATE, "plus", "minus").parameterTypes("long", "java.time.temporal.TemporalUnit"),
    instanceCall(JAVA_TIME_LOCAL_TIME, "plusHours", "plusMinutes", "plusSeconds", "plusNanos",
                 "minusHours", "minusMinutes", "minusSeconds", "minusNanos").parameterTypes("long"),
    instanceCall(JAVA_TIME_LOCAL_TIME, "plus", "minus").parameterTypes("long", "java.time.temporal.TemporalUnit"),
    instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "plusYears", "plusMonths", "plusWeeks", "plusDays", "plusHours", "plusMinutes", "plusSeconds",
                 "plusNanos",
                 "minusYears", "minusMonths", "minusWeeks", "minusDays", "minusHours", "minusMinutes", "minusSeconds",
                 "minusNanos").parameterTypes("long"),
    instanceCall(JAVA_TIME_LOCAL_DATE_TIME, "plus", "minus").parameterTypes("long", "java.time.temporal.TemporalUnit"),

    instanceCall(JAVA_TIME_OFFSET_TIME, "plusHours", "plusMinutes", "plusSeconds", "plusNanos",
                 "minusHours", "minusMinutes", "minusSeconds", "minusNanos").parameterTypes("long"),
    instanceCall(JAVA_TIME_OFFSET_TIME, "plus", "minus").parameterTypes("long", "java.time.temporal.TemporalUnit"),

    instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "plusYears", "plusMonths", "plusWeeks", "plusDays", "plusHours", "plusMinutes", "plusSeconds",
                 "plusNanos",
                 "minusYears", "minusMonths", "minusWeeks", "minusDays", "minusHours", "minusMinutes", "minusSeconds",
                 "minusNanos").parameterTypes("long"),
    instanceCall(JAVA_TIME_OFFSET_DATE_TIME, "plus", "minus").parameterTypes("long", "java.time.temporal.TemporalUnit")
  );

  private static final Map<String, SpecialField> SPECIAL_FIELDS = Map.of(JAVA_TIME_LOCAL_DATE, SpecialField.LOCAL_DATE_EPOCH_DAYS,
                                                                         JAVA_TIME_LOCAL_TIME, SpecialField.LOCAL_TIME_DAY_NANOSECONDS,
                                                                         JAVA_TIME_LOCAL_DATE_TIME,
                                                                         SpecialField.LOCAL_DATE_TIME_COMPARE_VALUE);
  public static final String UNSUPPORTED_TEMPORAL_TYPE_EXCEPTION = "java.time.temporal.UnsupportedTemporalTypeException";

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    if (!PLUS_MINUS_TO_JAVA_TIME.test(call)) return false;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return false;
    PsiType qualifierType = qualifier.getType();
    if (qualifierType == null) {
      return false;
    }
    int sign = 1;
    String name = call.getMethodExpression().getReferenceName();
    if (name == null) {
      return false;
    }
    if (name.startsWith("minus")) {
      sign = -1;
    }

    if (call.getArgumentList().getExpressions().length == 1) {
      plusMinusWith1Parameter(builder, call, qualifier, sign);
    }
    else {
      plusMinusWith2Parameters(builder, call, qualifier, sign);
    }
    return true;
  }

  private static void plusMinusWith2Parameters(@NotNull CFGBuilder builder,
                                               @NotNull PsiMethodCallExpression call,
                                               PsiExpression qualifier,
                                               int sign) {
    final PsiType qualifierType = qualifier.getType();
    if (qualifierType == null) {
      return;
    }
    SpecialField specialField = SPECIAL_FIELDS.get(qualifierType.getCanonicalText());

    PsiType chronoUnitType = JavaPsiFacade.getElementFactory(call.getProject())
      .createTypeFromText("java.time.temporal.ChronoUnit", call);

    CFGBuilder cfgBuilder = builder.pushExpression(qualifier) //qualifier
      .dup();  //qualifier qualifier
    if (specialField != null) {
      cfgBuilder = cfgBuilder
        .unwrap(specialField); //qualifier qualifierField
    }
    cfgBuilder = cfgBuilder
      .swap()  // qualifierField qualifier
      .pushExpression(call.getArgumentList().getExpressions()[0]) // qualifierField qualifier value
      .dup() // qualifierField qualifier value value
      .pushExpression(call.getArgumentList().getExpressions()[1]) // qualifierField qualifier value value temporalType
      .dup() // qualifierField qualifier value value temporalType temporalType
      .dup() // qualifierField qualifier value value temporalType temporalType temporalType
      .push(DfTypes.typedObject(chronoUnitType,
                                Nullability.NOT_NULL))  // qualifierField qualifier value value temporalType temporalType temporalType chrono
      .ifCondition(RelationType.IS)   // qualifierField qualifier value value temporalType temporalType
        .unwrap(SpecialField.ENUM_ORDINAL); // qualifierField qualifier value value temporalType enumOrdinal
    cfgBuilder = switch (qualifierType.getCanonicalText()) {
      case JAVA_TIME_LOCAL_DATE -> cfgBuilder
        .ensure(RelationType.GE, DfTypes.intValue(7), new ContractFailureProblem(call), UNSUPPORTED_TEMPORAL_TYPE_EXCEPTION)
        .ensure(RelationType.LE, DfTypes.intValue(14), new ContractFailureProblem(call), UNSUPPORTED_TEMPORAL_TYPE_EXCEPTION);
      case JAVA_TIME_LOCAL_TIME, JAVA_TIME_OFFSET_TIME -> cfgBuilder
        .ensure(RelationType.GE, DfTypes.intValue(0), new ContractFailureProblem(call), UNSUPPORTED_TEMPORAL_TYPE_EXCEPTION)
        .ensure(RelationType.LE, DfTypes.intValue(6), new ContractFailureProblem(call), UNSUPPORTED_TEMPORAL_TYPE_EXCEPTION);
      case JAVA_TIME_LOCAL_DATE_TIME, JAVA_TIME_OFFSET_DATE_TIME -> cfgBuilder
        .ensure(RelationType.GE, DfTypes.intValue(0), new ContractFailureProblem(call), UNSUPPORTED_TEMPORAL_TYPE_EXCEPTION)
        .ensure(RelationType.LE, DfTypes.intValue(14), new ContractFailureProblem(call), UNSUPPORTED_TEMPORAL_TYPE_EXCEPTION);
      default -> cfgBuilder;
    };
    cfgBuilder = cfgBuilder
        .pop() // qualifierField qualifier(3) value(2) value(1) temporalType(0)
        .splice(4, 1, 3, 2, 0)//qualifierField value(1) qualifier(3) value(2) temporalType(0)
        .call(call) // qualifierField value resultType
        .assignTo(builder.createTempVariable(qualifierType)); // qualifierField value resVariable
    cfgBuilder = processContract(sign, specialField, cfgBuilder);
    cfgBuilder
      .elseBranch() // qualifierField(5) qualifier(4) value(3) value(2) temporalType(1) temporalType(0)
        .splice(6, 4, 2, 1)// qualifier(4) value(2) temporalType(1)
        .call(call) // qualifierField value resultType
      .end();
  }

  private static void plusMinusWith1Parameter(@NotNull CFGBuilder builder,
                                              @NotNull PsiMethodCallExpression call,
                                              PsiExpression qualifier,
                                              int sign) {
    final PsiType qualifierType = qualifier.getType();
    if (qualifierType == null) {
      return;
    }

    SpecialField specialField = SPECIAL_FIELDS.get(qualifierType.getCanonicalText());

    CFGBuilder cfgBuilder = builder.pushExpression(qualifier) //qualifier
      .dup();  //qualifier qualifier
    if (specialField != null) {
      cfgBuilder = cfgBuilder
        .unwrap(specialField); //qualifier qualifierField
    }
    cfgBuilder = cfgBuilder
      .swap()  // qualifierField qualifier
      .pushExpression(call.getArgumentList().getExpressions()[0]) // qualifierField qualifier value
      .dup() // qualifierField(3) qualifier(2) value(1) value(0)
      .splice(4, 3, 0, 2, 1) // qualifierField(3)  value(0) qualifier(2) value(1)
      .call(call) // qualifierField value resultType
      .assignTo(builder.createTempVariable(qualifierType)); // qualifierField value resVariable
    processContract(sign, specialField, cfgBuilder);
  }

  /**
   * Expected condition: qualifierField value resVariable
   */
  private static CFGBuilder processContract(int sign, SpecialField specialField, CFGBuilder cfgBuilder) {
    cfgBuilder = cfgBuilder.dup(); // qualifierField value resVariable resVariable
    if (specialField != null) {
      cfgBuilder = cfgBuilder
        .unwrap(specialField); // qualifierField(3) value(2) resVariable(1) resField(0)
    }
    return cfgBuilder
      .splice(4, 1, 3, 0, 2) //resValue(1) qualifierField(3) resField(0) value(2)
      .dup() //resVariable qualifierField resField value value
      .push(DfTypes.intValue(0))  //resVariable qualifierField resField value value 0
      .ifCondition(RelationType.EQ)  //resVariable qualifierField resField value
        .pop() //resVariable qualifierField resField
        .compare(RelationType.EQ) //resVariable compareResult
        .ensure(RelationType.EQ, DfTypes.TRUE, new UnsatisfiedConditionProblem() {
        }, null)
        .pop()
      .elseBranch()
        .push(DfTypes.intValue(0))  //resVariable qualifierField resField value 0
        //if sign >0 -> +1 else -> -1
        .ifCondition(sign > 0 ? RelationType.GT : RelationType.LT) //resVariable qualifierField resField
          //if +1 -> qualifierField < resField
          .compare(RelationType.LT) //resVariable compareResult
          .ensure(RelationType.EQ, DfTypes.TRUE, new UnsatisfiedConditionProblem() {
          }, null)
          .pop()  //resVariable
        .elseBranch()
          //if -1 -> qualifierField>resField
          .compare(RelationType.GT) //resVariable compareResult
          .ensure(RelationType.EQ, DfTypes.TRUE, new UnsatisfiedConditionProblem() {
          }, null)
          .pop()  //resVariable
        .end()
      .end();
  }
}
