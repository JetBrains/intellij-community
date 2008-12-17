package com.intellij.compiler.classParsing;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.util.ArrayUtil;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 4, 2004
 */
public class GenericMethodSignature {
  private final String myFormalTypeParams;
  private final String[] myParamSignatures;
  private final String myReturnTypeSignature;
  private final String myThrowsSignature;

  private GenericMethodSignature(String formalTypeParams, String[] paramSignatures, String returnTypeSignature, String throwsSignature) {
    myFormalTypeParams = formalTypeParams;
    myParamSignatures = paramSignatures;
    myReturnTypeSignature = returnTypeSignature;
    myThrowsSignature = throwsSignature;
  }

  public String getFormalTypeParams() {
    return myFormalTypeParams;
  }

  public String[] getParamSignatures() {
    return myParamSignatures;
  }

  public String getReturnTypeSignature() {
    return myReturnTypeSignature;
  }

  public String getThrowsSignature() {
    return myThrowsSignature;
  }

  public static GenericMethodSignature parse(String methodSignature) throws SignatureParsingException {
    final StringCharacterIterator it = new StringCharacterIterator(methodSignature);

    final StringBuilder formals = new StringBuilder();
    if (it.current() == '<') {
      SignatureParser.INSTANCE.parseFormalTypeParameters(it, formals);
    }

    if (it.current() != '(') {
      throw new SignatureParsingException(CompilerBundle.message("error.signature.parsing.expected.other.symbol", "(", formals.toString()));
    }

    it.next(); // skip '('

    final String[] paramSignatures;
    if (it.current() != ')') {
      final List<String> params = new ArrayList<String>();
      while (it.current() != ')') {
        final StringBuilder typeSignature = new StringBuilder();
        SignatureParser.INSTANCE.parseTypeSignature(it, typeSignature);
        params.add(typeSignature.toString());
      }
      paramSignatures = ArrayUtil.toStringArray(params);
    }
    else {
      paramSignatures = ArrayUtil.EMPTY_STRING_ARRAY;
    }
    it.next(); // skip ')'

    final StringBuilder returnTypeSignature = new StringBuilder();
    SignatureParser.INSTANCE.parseReturnType(it, returnTypeSignature);

    final StringBuilder throwsSignature = new StringBuilder();
    if (it.current() != CharacterIterator.DONE) {
      SignatureParser.INSTANCE.parseThrowsSignature(it, throwsSignature);
    }
    
    return new GenericMethodSignature(formals.toString(), paramSignatures, returnTypeSignature.toString(), throwsSignature.toString());
  }
}
