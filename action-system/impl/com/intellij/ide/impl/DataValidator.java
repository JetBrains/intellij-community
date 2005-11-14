package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;

public abstract class DataValidator <T> {

  Logger LOG = Logger.getInstance("#com.intellij.ide.impl.DataValidator");

  private static final HashMap<String, DataValidator> ourValidators = new HashMap<String, DataValidator>();
  private static final DataValidator<VirtualFile> VIRTUAL_FILE_VALIDATOR = new DataValidator<VirtualFile>() {
    public VirtualFile findInvalid(VirtualFile file) {
      return file.isValid() ? null : file;
    }
  };
  private static final DataValidator<PsiElement> PSI_ELEMENT_VALIDATOR = new DataValidator<PsiElement>() {
    public PsiElement findInvalid(PsiElement psiElement) {
      return psiElement.isValid() ? null : psiElement;
    }
  };

  public abstract T findInvalid(T data);

  public static DataValidator getValidator(String dataId) {
    return ourValidators.get(dataId);
  }

  public static Object findInvalidData(String dataId, Object data) {
    if (data == null) return null;
    DataValidator validator = getValidator(dataId);
    if (validator != null) return validator.findInvalid(data);
    return null;
  }

  static {
    ourValidators.put(DataConstants.VIRTUAL_FILE, VIRTUAL_FILE_VALIDATOR);
    ourValidators.put(DataConstants.VIRTUAL_FILE_ARRAY, new ArrayValidator<VirtualFile>(VIRTUAL_FILE_VALIDATOR));
    ourValidators.put(DataConstants.PSI_ELEMENT, PSI_ELEMENT_VALIDATOR);
    ourValidators.put(DataConstantsEx.PSI_ELEMENT_ARRAY, new ArrayValidator<PsiElement>(PSI_ELEMENT_VALIDATOR));
    ourValidators.put(DataConstants.PSI_FILE, PSI_ELEMENT_VALIDATOR);
  }

  private static class ArrayValidator <T> extends DataValidator {
    private final DataValidator<T> myElementValidator;

    public ArrayValidator(DataValidator<T> elementValidator) {
      myElementValidator = elementValidator;
    }

    public T findInvalid(Object object) {
      Object[] array = (Object[])object;
      for (int i = 0; i < array.length; i++) {
        T element = (T)array[i];
        LOG.assertTrue(element != null);
        T invalid = myElementValidator.findInvalid(element);
        if (invalid != null) return invalid;
      }
      return null;
    }
  }
}
