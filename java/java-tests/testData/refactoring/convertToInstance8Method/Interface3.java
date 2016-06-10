interface PsiAntElement {
}

interface AntElement extends PsiAntElement {
}

class AntElementImpl implements AntElement {
}

interface AntNameIdentifier extends AntElement {
}

class AntNameIdentifierImpl extends AntElementImpl implements AntNameIdentifier {
}

interface AntStructuredElement extends AntElement {
}

class AntStructuredElementImpl extends AntElementImpl implements AntStructuredElement {
}

interface AntTask extends AntStructuredElement {
}

class AntTaskImpl extends AntStructuredElementImpl implements AntTask {
}

interface AntMacroDef extends AntTask {
}

class AntMacroDefImpl extends AntTaskImpl implements AntMacroDef {
}

class X {
    static void <caret>m(PsiAntElement i) {
    }
}