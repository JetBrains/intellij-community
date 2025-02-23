document.addEventListener("DOMContentLoaded", () => {
  const rows = document.getElementsByClassName("linkable-table-row")
  for (const row of rows) {
    const href = row.getAttribute("href")
    row.addEventListener("click", () => {
      window.open(href, "_blank");
    });
  }
});