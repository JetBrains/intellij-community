/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

package com.intellij.usages;

import com.intellij.openapi.components.ApplicationComponent;

/**
 * @author peter
 */
public interface UsageConvertor extends ApplicationComponent{

  Usage convert(Usage usage);

}
