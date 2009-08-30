package com.intellij.refactoring;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

public class RefactorJBundle{
    private static final ResourceBundle ourBundle =
            ResourceBundle.getBundle("com.intellij.refactoring.RefactorJBundle");

    private RefactorJBundle(){
    }

    public static String message(@PropertyKey(resourceBundle = "com.intellij.refactoring.RefactorJBundle")String key,
                                 Object... params){
        return CommonBundle.message(ourBundle, key, params);
    }
}
