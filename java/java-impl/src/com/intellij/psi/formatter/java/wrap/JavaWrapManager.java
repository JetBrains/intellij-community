/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.formatter.java.wrap;

import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Block;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.java.wrap.impl.JavaChildBlockWrapFactory;
import com.intellij.psi.formatter.java.wrap.impl.JavaChildWrapArranger;
import org.jetbrains.annotations.Nullable;

/**
 * Defines common contract for {@link Wrap wraps} manipulation during java {@link Block blocks} processing.
 * <p/>
 * This class is intended to serve as a facade for various fine-grained wrap processing classes.
 * <p/>
 * This class is not singleton but it's thread-safe and provides single-point-of-usage field {@link #INSTANCE}.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since Apr 21, 2010 2:19:17 PM
 */
public class JavaWrapManager {

  /** Single-point-of-usage field. */
  public static final JavaWrapManager INSTANCE = new JavaWrapManager();

  private final JavaChildWrapArranger myChildArranger;
  private final JavaChildBlockWrapFactory myChildBlockFactory;

  /**
   * Creates new <code>JavaWrapManager</code> object with default wrapping services.
   */
  public JavaWrapManager() {
    this(new JavaChildWrapArranger(), new JavaChildBlockWrapFactory());
  }

  public JavaWrapManager(JavaChildWrapArranger childWrapArranger, JavaChildBlockWrapFactory childBlockWrapFactory) {
    myChildArranger = childWrapArranger;
    myChildBlockFactory = childBlockWrapFactory;
  }

  /**
   * Tries to define the wrap to use for the {@link Block block} for the given <code>'child'</code> node. It's assumed that
   * given <code>'child'</code> node is descendant (direct or indirect) of the given <code>'parent'</code> node.
   * I.e. <code>'parent'</code> node defines usage context for the <code>'child'</code> node.
   *
   * @param child                   child node which {@link Wrap wrap} is to be defined
   * @param parent                  direct or indirect parent of the given <code>'child'</code> node. Defines usage context
   *                                of <code>'child'</code> node processing
   * @param settings                code style settings to use during wrap definition
   * @param suggestedWrap           wrap suggested to use by clients of current class. I.e. those clients offer wrap to
   *                                use based on their information about current processing state. However, it's possible
   *                                that they don't know details of fine-grained wrap definition algorithm encapsulated
   *                                at the current class. Hence, this method takes suggested wrap into consideration but
   *                                is not required to use it all the time node based on the given parameters
   * @param reservedWrapsProvider   reserved {@code 'element type -> wrap instance'} mappings provider. <b>Note:</b> this
   *                                argument is considered to be a part of legacy heritage and is intended to be removed as
   *                                soon as formatting code refactoring is done
   * @return                        wrap to use for the given <code>'child'</code> node if it's possible to define the one;
   *                                <code>null</code> otherwise
   */
  @Nullable
  public Wrap arrangeChildWrap(ASTNode child, ASTNode parent, CodeStyleSettings settings, Wrap suggestedWrap,
                               ReservedWrapsProvider reservedWrapsProvider) 
  {
    return myChildArranger.arrange(child, parent, settings, suggestedWrap, reservedWrapsProvider);
  }

  /**
   * Creates {@link Wrap wrap} to be used with the children blocks of the the given block.
   *
   * @param block                   target block which sub-blocks should use wrap created by the current method
   * @param settings                code formatting settings to consider during wrap construction
   * @param reservedWrapsProvider   reserved {@code 'element type -> wrap instance'} mappings provider. <b>Note:</b> this
   *                                argument is considered to be a part of legacy heritage and is intended to be removed as
   *                                soon as formatting code refactoring is done
   * @return                        wrap to use for the sub-blocks of the given block
   */
  @Nullable
  public Wrap createChildBlockWrap(ASTBlock block, CodeStyleSettings settings, ReservedWrapsProvider reservedWrapsProvider) {
    return myChildBlockFactory.create(block, settings, reservedWrapsProvider);
  }
}
