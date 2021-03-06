// "Implement 'Parent'" "true"
sealed interface Parent permits User<caret> {}

record User(int age) {}