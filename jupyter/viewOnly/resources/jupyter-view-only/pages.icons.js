
      function __appendSprite(sprite) {
  let container = document.createElement("div");
  container.style.width = 0;
  container.style.height = 0;
  container.style.position = "absolute";
  container.style.overflow = "hidden";
  container.innerHTML = sprite;
  document.body.append(container);
};
      __appendSprite('<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"></svg>');
  