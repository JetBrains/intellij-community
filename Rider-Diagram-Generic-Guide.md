# JetBrains 플랫폼용 범용 클래스 다이어그램 구현 가이드 (개선판)

## 1. 개요

이 문서는 IntelliJ 플랫폼(Rider, IntelliJ IDEA 등) 기반의 IDE에서 새로운 프로그래밍 언어를 위한 클래스 다이어그램 및 상속 다이어그램 기능을 구현하는 방법에 대한 범용적인 지침을 제공합니다.

JetBrains 플랫폼의 다이어그램 기능은 확장 지점(Extension Point)을 통해 IDE의 핵심 기능을 확장하는 방식으로 구현됩니다. 개발자는 특정 언어의 소스 코드를 분석하여 클래스, 인터페이스, 상속 관계 등의 구조를 파악하고, 이를 플랫폼이 이해할 수 있는 다이어그램 데이터 모델로 변환하는 로직을 작성해야 합니다.

**이 과정의 3가지 핵심 요소:**

1.  **PSI (Program Structure Interface)**: IDE가 소스 코드를 이해하는 핵심적인 추상화 계층입니다. 특정 언어용 플러그인은 해당 언어의 파일 구조를 표현하는 PSI 트리 구현을 제공합니다. 개발자는 이 PSI 트리를 탐색하여 클래스 이름, 메서드, 상속 관계 등의 정보를 추출해야 합니다. **이것이 다이어그램 구현에서 가장 중요한 부분입니다.**
2.  **Diagram API**: PSI를 통해 얻은 정보를 바탕으로 다이어그램을 구성하는 데 필요한 핵심 API입니다. `com.intellij.diagram` 패키지의 `DiagramProvider`, `DiagramDataModel` 등의 클래스를 사용하여 다이어그램의 동작과 데이터를 정의합니다.
3.  **확장 지점 (Extension Point)**: 개발한 다이어그램 기능을 플러그인 형태로 IDE에 등록하기 위한 메커니즘입니다. `plugin.xml` 파일에 `<diagram.provider>`와 같이 기술합니다.

## 2. 핵심 API 및 확장 지점

### `com.intellij.diagram.DiagramProvider`

다이어그램 기능의 주된 진입점(Entry Point) 역할을 하는 추상 클래스입니다. 새로운 종류의 다이어그램을 만들려면 이 클래스를 상속받아 구현해야 합니다.

-   **주요 메서드:**
    -   `getID()`: 다이어그램 제공자의 고유 ID를 반환합니다. (예: `"MyLanguageUML"`)
    -   `getPresentableName()`: "Show Diagram" 팝업 메뉴 등에 표시될 다이어그램의 이름을 반환합니다. (예: `"My Language Classes"`)
    -   `createDataModel(...)`: 다이어그램에 표시될 노드(Node)와 엣지(Edge)를 구성하는 데이터 모델을 생성합니다. 이 메서드 내부에서 PSI 분석이 주로 이루어집니다.

### `com.intellij.diagram.DiagramDataModel`

다이어그램의 전체 데이터 구조를 관리하는 클래스입니다. `DiagramNode`와 `DiagramEdge`의 컬렉션을 포함하며, 다이어그램의 레이아웃과 상호작용에 필요한 데이터를 제공합니다. `GraphvizDiagramDataModel`과 같이 특정 레이아웃 엔진을 사용하는 구현체도 존재합니다.

### `DiagramNode` & `DiagramEdge`

-   `DiagramNode`: 다이어그램에 표시되는 각각의 요소(예: 클래스, 인터페이스)를 나타냅니다. 일반적으로 특정 `PsiElement`에 연결되며, 노드를 더블클릭했을 때 해당 소스 코드로 이동하는 기능을 제공합니다.
-   `DiagramEdge`: 노드 간의 관계(예: 상속, 구현)를 나타내는 선입니다. `DiagramRelationshipInfo`를 통해 관계의 종류(예: `INHERITANCE`), 선의 종류(실선, 점선) 등을 정의할 수 있습니다.

### `plugin.xml` 내 확장 지점

개발한 `DiagramProvider` 구현체를 IDE에 등록하려면 `plugin.xml` 파일의 `<extensions>` 섹션에 다음과 같이 선언해야 합니다.

```xml
<extensions defaultExtensionNs="com.intellij">
  <diagram.provider implementation="com.your.package.MyLanguageDiagramProvider"/>
</extensions>
```

## 3. 구현 단계

1.  **`DiagramProvider` 상속 및 구현**:
    -   자신만의 `DiagramProvider` 클래스를 만들고 필수 메서드를 구현합니다.
    -   `createDataModel` 메서드에서 다이어그램 생성의 시작점이 되는 `PsiElement`를 받아옵니다.

2.  **PSI 트리 분석 및 데이터 모델 구축 (핵심 로직)**:
    -   `createDataModel` 내부에서 재귀적인 헬퍼 함수를 호출하여 PSI 분석을 시작합니다.
    -   `PsiTreeUtil.findChildrenOfType` 등의 유틸리티를 사용하여 파일 내의 모든 클래스 PSI 요소를 찾습니다.
    -   각 클래스 PSI 요소에서 이름, 슈퍼 클래스, 멤버(필드, 메서드) 등의 정보를 추출합니다.
    -   추출한 정보를 바탕으로 `DiagramNode`와 `DiagramEdge` 객체를 생성합니다.
    -   생성된 객체들을 `DiagramDataModel`에 담아 반환합니다.

3.  **액션 등록 (선택 사항)**:
    -   사용자가 에디터의 컨텍스트 메뉴나 프로젝트 뷰에서 다이어그램을 쉽게 생성할 수 있도록 `AnAction`을 상속받는 액션을 `plugin.xml`에 등록할 수 있습니다. 이 액션은 현재 컨텍스트의 `PsiElement`를 가져와 `DiagramProvider`를 호출하는 역할을 합니다.

## 4. 가상 예제 코드 (Kotlin)

다음은 개념을 설명하기 위한 구조화된 가상 코드 스니펫입니다. (`MyLangClassElement`는 각 언어 플러그인에서 제공하는 PSI 클래스를 의미합니다.)

```kotlin
import com.intellij.diagram.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

// 1. DiagramProvider 구현
class MyLanguageDiagramProvider : DiagramProvider<PsiElement>() {
    // ... getID(), getPresentableName() 등 ...

    override fun createDataModel(project: Project, element: PsiElement?, ...): DiagramDataModel<PsiElement> {
        val nodes = mutableListOf<DiagramNode<PsiElement>>()
        val edges = mutableListOf<DiagramEdge<PsiElement>>()
        val processedElements = mutableSetOf<MyLangClassElement>()

        // Find the starting class element from the current context
        val startElement = PsiTreeUtil.getParentOfType(element, MyLangClassElement::class.java)
        if (startElement != null) {
            buildGraph(startElement, nodes, edges, processedElements)
        }

        return MyDiagramDataModel(project, nodes, edges, this)
    }

    // 2. 재귀적인 PSI 분석 함수
    private fun buildGraph(
        currentClass: MyLangClassElement,
        nodes: MutableList<DiagramNode<PsiElement>>,
        edges: MutableList<DiagramEdge<PsiElement>>,
        processed: MutableSet<MyLangClassElement>
    ) {
        if (processed.contains(currentClass)) return
        processed.add(currentClass)

        val classNode = MyDiagramNode(currentClass, this)
        nodes.add(classNode)

        // 슈퍼 클래스를 찾아 엣지를 추가하고, 재귀 호출
        currentClass.superClassElement?.let { superClass ->
            val superClassNode = MyDiagramNode(superClass, this)
            val relationship = DiagramRelationshipInfo.INHERITANCE
            edges.add(DiagramEdge(classNode, superClassNode, relationship))
            buildGraph(superClass, nodes, edges, processed)
        }
    }
}

// (가상) 언어별 PSI 클래스
interface MyLangClassElement : PsiElement {
    val superClassElement: MyLangClassElement?
}

// 3. 커스텀 노드/데이터 모델 (선택 사항)
// 노드의 모양이나 툴팁을 커스터마이징할 수 있다.
class MyDiagramNode(element: MyLangClassElement, provider: DiagramProvider<*>)
    : PsiDiagramNode<MyLangClassElement>(element, provider) {
    // ... override 메서드로 커스터마이징 ...
}

class MyDiagramDataModel(
    project: Project,
    nodes: List<DiagramNode<PsiElement>>,
    edges: List<DiagramEdge<PsiElement>>,
    provider: DiagramProvider<PsiElement>
) : VfsDiagramDataModel<PsiElement>(project, provider, nodes.toTypedArray(), edges.toTypedArray()) {
    // ...
}
```
