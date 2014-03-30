class Test<<error descr="Cyclic inheritance involving 'T'"></error>T extends T> {}
class Test1<<error descr="Cyclic inheritance involving 'T'"></error>T extends S, S extends T> {}
