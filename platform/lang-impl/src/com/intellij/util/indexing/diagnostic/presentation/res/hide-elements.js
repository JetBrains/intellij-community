function hideElementsHavingClass(className, hideOrShow) {
  const elements = document.getElementsByClassName(className)
  const displayType = hideOrShow ? 'none' : 'initial'
  for (const element of elements) {
    element.classList.toggle('invisible', hideOrShow)
  }
}