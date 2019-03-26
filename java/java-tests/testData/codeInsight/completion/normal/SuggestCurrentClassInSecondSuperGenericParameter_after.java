interface Super<A,B> {}
class Sub implements Super<Sub, Sub<caret>> {}