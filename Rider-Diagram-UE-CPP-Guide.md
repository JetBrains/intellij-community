# Rider용 언리얼 엔진 C++ 클래스 다이어그램 구현 가이드 (개선판)

## 1. 개요

이 문서는 JetBrains Rider에서 언리얼 엔진(UE) C++ 프로젝트를 위한 클래스 다이어그램 기능을 구현하는 상세한 방법을 제공합니다. 이 가이드는 [범용 클래스 다이어그램 구현 가이드](Rider-Diagram-Generic-Guide.md)에서 설명한 기본 개념 위에 구축됩니다.

Rider는 Clang 기반의 자체 C++ 파서를 사용하여 코드를 분석하고 PSI(Program Structure Interface) 트리를 구축합니다. C++ 코드 구조는 **`com.jetbrains.cidr.lang.psi`** 패키지에 포함된 `OCFile`, `OCClass` 등의 PSI 클래스를 통해 접근할 수 있습니다. 이 가이드는 해당 API를 활용하는 방법을 설명합니다. (참고: 이 API는 공식 문서가 없으므로, 이 가이드와 같은 오픈소스 플러그인 분석을 통해 사용법을 익혀야 합니다.)

**이 가이드의 목표:**
1.  표준 C++ 클래스(상속, 멤버 포함)를 다이어그램으로 시각화합니다.
2.  언리얼 엔진의 리플렉션 매크로(`UCLASS`, `UPROPERTY` 등)를 분석하여 다이어그램에 추가적인 상세 정보를 표현합니다.

## 2. 기본 C++ 클래스 다이어그램 구현

### 2.1. C++ 용 DiagramProvider 구현

`DiagramProvider`의 핵심 로직은 `createDataModel` 메서드 내부에 구현됩니다. 여기서 `PsiTreeUtil`을 사용하여 주어진 PSI 요소(예: 파일) 내의 모든 C++ 클래스(`OCClass`)를 찾고 다이어그램 데이터 처리를 시작합니다.

```kotlin
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.cidr.lang.psi.OCClass
import com.jetbrains.cidr.lang.psi.OCFile
// ... other imports

class UeCppDiagramProvider : DiagramProvider<PsiElement>() {

    override fun getID(): String = "UeCppDiagramProvider"
    override fun getPresentableName(): String = "Unreal C++ Class Diagram"

    override fun createDataModel(
        project: Project,
        element: PsiElement?,
        // ...
    ): DiagramDataModel<PsiElement> {
        val nodes = mutableListOf<DiagramNode<PsiElement>>()
        val edges = mutableListOf<DiagramEdge<PsiElement>>()
        val processedClasses = mutableSetOf<OCClass>()

        // 시작점(element)이 파일이면 파일 내 모든 클래스를, 특정 클래스면 해당 클래스만 처리
        val startClass = element as? OCClass ?: PsiTreeUtil.getParentOfType(element, OCClass::class.java)
        if (startClass != null) {
            processClass(startClass, nodes, edges, processedClasses)
        } else {
            val file = element?.containingFile as? OCFile
            PsiTreeUtil.findChildrenOfType(file, OCClass::class.java).forEach { ocClass ->
                processClass(ocClass, nodes, edges, processedClasses)
            }
        }

        return UeCppDiagramDataModel(project, nodes, edges, this)
    }
    // ...
}
```

### 2.2. C++ PSI 탐색 및 데이터 모델 구축

재귀 함수를 사용하여 클래스를 처리하고, 이미 처리된 클래스는 건너뛰어 무한 루프를 방지합니다.

-   **클래스 노드**: `OCClass`를 `DiagramNode`로 변환합니다.
-   **상속 엣지**: `OCClass.superClasses` 프로퍼티를 사용하여 부모 클래스를 가져오고, `DiagramEdge`를 생성합니다.

```kotlin
private fun processClass(
    ocClass: OCClass,
    nodes: MutableList<DiagramNode<PsiElement>>,
    edges: MutableList<DiagramEdge<PsiElement>>,
    processedClasses: MutableSet<OCClass>
) {
    if (processedClasses.contains(ocClass)) {
        return
    }
    processedClasses.add(ocClass)

    val classNode = UeCppDiagramNode(ocClass, this) // 커스텀 노드
    nodes.add(classNode)

    // 부모 클래스 관계 탐색
    ocClass.superClasses.forEach { superClass ->
        // superClass는 OCReferenceElement이므로, 실제 OCClass 정의를 찾아야 함
        val resolvedSuperClass = superClass.resolve() as? OCClass ?: return@forEach

        val superClassNode = UeCppDiagramNode(resolvedSuperClass, this)
        val relationship = DiagramRelationshipInfo.INHERITANCE
        edges.add(DiagramEdge(classNode, superClassNode, relationship))

        // 부모 클래스도 재귀적으로 처리
        processClass(resolvedSuperClass, nodes, edges, processedClasses)
    }
}
```

## 3. 언리얼 엔진 C++ 확장 기능 구현

### 3.1. UCLASS / USTRUCT 매크로 분석

클래스 바로 위에 선언된 매크로를 찾아 그 종류를 식별합니다.

-   **매크로 확인**: `PsiTreeUtil.getPrevSiblingOfType`을 사용하여 클래스 선언 이전에 나오는 `OCMacroCall`을 찾습니다.
-   **시각적 구분**: 매크로 이름(`UCLASS`, `USTRUCT`)에 따라 노드의 스타일(색상, 아이콘)을 다르게 설정합니다.

```kotlin
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.cidr.lang.psi.OCClass
import com.jetbrains.cidr.lang.psi.OCMacroCall

// UeCppDiagramNode 클래스 내부 또는 헬퍼 함수
fun getUnrealMacroType(ocClass: OCClass): String? {
    val macroCall = PsiTreeUtil.getPrevSiblingOfType(ocClass, OCMacroCall::class.java)
    val macroName = macroCall?.name
    return if (macroName == "UCLASS" || macroName == "USTRUCT") {
        macroName
    } else {
        null
    }
}

// 매크로 인자 파싱 (예: UCLASS(Blueprintable))
fun getMacroSpecifiers(macroCall: OCMacroCall): List<String> {
    // macroCall.argumentList.text 를 파싱하여 "Blueprintable" 등의 지정자를 추출
    return macroCall.argumentList?.text?.removeSurrounding("(", ")")?.split(",")?.map { it.trim() } ?: emptyList()
}
```

### 3.2. UPROPERTY / UFUNCTION 멤버 분석

클래스 필드(`OCField`)나 메서드(`OCMethod`)에 연결된 매크로를 분석합니다.

-   **매크로 파싱**: `OCField` 또는 `OCMethod` 이전에 나오는 `OCMacroCall`을 위와 동일한 방식으로 찾습니다.
-   **정보 표시**: 노드 내 멤버 목록에 `(EditAnywhere)`, `(BlueprintCallable)`과 같은 파싱된 지정자를 함께 표시합니다.

### 3.3. UPROPERTY를 통한 액터 참조 관계 시각화

`UPROPERTY`로 선언된 포인터 타입의 필드를 식별하여 클래스 간의 '참조' 관계를 표현합니다.

-   **참조 관계 식별**: `OCField`의 타입(`OCType`)이 포인터(`isPointer()`)이거나 `TSubclassOf`와 같은 특정 템플릿인지 확인합니다.
-   **커스텀 엣지 생성**: 상속과 다른 스타일(예: 점선)을 가진 커스텀 `DiagramEdge`를 생성하여 참조 관계를 시각화합니다.

```kotlin
import com.jetbrains.cidr.lang.psi.OCField
import com.jetbrains.cidr.lang.psi.OCType
import com.jetbrains.cidr.lang.psi.OCDeclarator

// processClass 함수 내부에 추가될 로직
private fun processProperties(ocClass: OCClass, classNode: DiagramNode<PsiElement>, edges: MutableList<...>) {
    ocClass.fields.forEach { field ->
        val macroCall = PsiTreeUtil.getPrevSiblingOfType(field, OCMacroCall::class.java)
        if (macroCall?.name != "UPROPERTY") return@forEach

        val fieldType = field.type
        // 포인터 타입이거나 TSubclassOf<> 같은 템플릿인지 확인 (구체적인 API는 추가 조사가 필요할 수 있음)
        if (fieldType.isPointer() || fieldType.text.startsWith("TSubclassOf")) {
            val referencedClass = resolveTypeToOCClass(fieldType) // OCType에서 OCClass를 찾아내는 로직
            if (referencedClass != null) {
                val referencedNode = UeCppDiagramNode(referencedClass, this)
                // 커스텀 관계 "UE_REFERENCE" 정의
                val relationship = DiagramRelationshipInfo("UE_REFERENCE", DiagramLineType.DASHED, "references")
                edges.add(DiagramEdge(classNode, referencedNode, relationship))
            }
        }
    }
}

// 타입을 OCClass로 변환하는 헬퍼 함수 (구현 필요)
fun resolveTypeToOCClass(type: OCType): OCClass? {
    // type.declarationElement.resolve() 등을 사용하여 실제 클래스 정의를 찾아야 함
    return (type.declarationElement?.resolve() as? OCDeclarator)?.parent as? OCClass
}
```

## 4. 결론

Rider에서 언리얼 엔진 C++ 클래스 다이어그램을 구현하는 것은 `com.jetbrains.cidr.lang.psi` API에 대한 이해가 필수적입니다. 이 가이드에서 제시한 `PsiTreeUtil`을 활용한 PSI 트리 탐색 및 실제 클래스(`OCClass`, `OCMacroCall` 등)를 사용하는 코드 예시는 단순한 개념을 넘어, 실제 구현에 직접 적용할 수 있는 구체적인 출발점을 제공합니다.
