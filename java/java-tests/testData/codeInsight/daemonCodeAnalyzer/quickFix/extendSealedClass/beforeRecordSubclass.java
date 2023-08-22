// "Make 'User' implement 'Parent'" "true-preview"
sealed interface Parent permits User<caret> {}

record User(int age) {}