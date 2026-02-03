// "Make 'Child' extend 'Parent'" "true-preview"
sealed interface Parent permits Child {}

non-sealed interface Child extends Parent {}