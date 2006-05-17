package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashMap;

import java.lang.reflect.Array;
import java.util.Map;

public abstract class DataValidator <T> {

  Logger LOG = Logger.getInstance("#com.intellij.ide.impl.DataValidator");

  private static final Map<String, DataValidator> ourValidators = new HashMap<String, DataValidator>();
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

  private static <T> DataValidator<T> getValidator(String dataId) {
    return ourValidators.get(dataId);
  }

  public static <T> T findInvalidData(String dataId, T data) {
    if (data == null) return null;
    DataValidator<T> validator = getValidator(dataId);
    if (validator != null) return validator.findInvalid(data);
    return null;
  }

  static {
    ourValidators.put(DataConstants.VIRTUAL_FILE, VIRTUAL_FILE_VALIDATOR);
    ourValidators.put(DataConstants.VIRTUAL_FILE_ARRAY, new ArrayValidator<VirtualFile>(VIRTUAL_FILE_VALIDATOR));
    ourValidators.put(DataConstants.PSI_ELEMENT, PSI_ELEMENT_VALIDATOR);
    ourValidators.put(DataConstants.PSI_ELEMENT_ARRAY, new ArrayValidator<PsiElement>(PSI_ELEMENT_VALIDATOR));
    ourValidators.put(DataConstants.PSI_FILE, PSI_ELEMENT_VALIDATOR);
  }

  private static class ArrayValidator<T> extends DataValidator<T[]> {
    private final DataValidator<T> myElementValidator;

    public ArrayValidator(DataValidator<T> elementValidator) {
      myElementValidator = elementValidator;
    }

    public T[] findInvalid(T[] array) {
      for (T element : array) {
        LOG.assertTrue(element != null);
        T invalid = myElementValidator.findInvalid(element);
        if (invalid != null) {
          T[] result = (T[])Array.newInstance(array[0].getClass(), 1);
          result[0] = invalid;
          return result;
        }
      }
      return null;
    }
  }
}
