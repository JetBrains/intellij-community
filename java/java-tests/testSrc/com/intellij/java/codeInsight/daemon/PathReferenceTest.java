// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class PathReferenceTest extends LightJavaCodeInsightFixtureTestCase {

  public void testPathReference() {

    myFixture.configureByText(JavaFileType.INSTANCE, """
      class Test {
          String foo = "/Appl<caret>ications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swiftc -incremental -module-name RxSwift -Onone -D COCOAPODS -sdk /Applications/Xcode.app/Contents/Developer/Platforms/iPhoneSimulator.platform/Developer/SDKs/iPhoneSimulator9.2.sdk -target i386-apple-ios8.0 -g -module-cache-path /Users/jetbrains/Library/Caches/AppCode34/DerivedData/SwiftProject-cc897c54/ModuleCache -Xfrontend -serialize-debugging-options -enable-testing -I /Users/jetbrains/Library/Caches/AppCode34/DerivedData/SwiftProject-cc897c54/Build/Products/Debug-iphonesimulator -F /Users/jetbrains/Library/Caches/AppCode34/DerivedData/SwiftProject-cc897c54/Build/Products/Debug-iphonesimulator -c - j8 / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Observables / Implementations / AddRef.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Observables / Implementations / Amb.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Disposables / AnonymousDisposable.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Schedulers / Internal / AnonymousInvocable.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Observables / Implementations / AnonymousObservable.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Observers / AnonymousObserver.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / AnyObserver.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Concurrency / AsyncLock.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / DataStructures / Bag.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Subjects / BehaviorSubject.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Disposables / BinaryDisposable.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Disposables / BooleanDisposable.swift / Users / jetbrains / Demos / SwiftProject / Pods / RxSwift / RxSwift / Observables / Implementations";
      }""");
    PsiReference reference = myFixture.getReferenceAtCaretPosition();
    PsiReference[] references = reference.getElement().getReferences();
    assertEquals(1, references.length);
  }
}
