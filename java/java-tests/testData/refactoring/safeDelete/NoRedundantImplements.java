interface P {}

interface A extends P {}
interface <caret>B extends P {}

class C implements A, B {}