// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import Tippy from "tippy.js"

export class NodeTooltipManager {
  constructor(cy) {
    this.tippy = null
    cy.on("tap", "node", function (event) {
      const node = event.target
      const ref = node.popperRef()

      if (this.tippy == null) {
        const host = document.getElementById("tooltip")
        this.tippy = new Tippy(host, {
          getReferenceClientRect: ref.getBoundingClientRect,
          trigger: "manual",
          appendTo: host,
          interactive: true,
          allowHTML: true,
          content: buildTooltipContent(node.data()),
        })
      }
      else {
        this.tippy.setProps({
          getReferenceClientRect: ref.getBoundingClientRect,
          content: buildTooltipContent(node.data()),
        })
      }

      this.tippy.show()
    })
  }
}

function buildTooltipContent(item) {
  const isPackageSet = item.package != null && item.package.length !== 0

  // for us is very important to understand dependencies between source modules, that's why on grap source module name is used
  // for plugins as node name
  const lines = [
    {name: item.name, value: null, main: true},
    {name: "package", value: isPackageSet ? item.package : "not set", extraStyle: isPackageSet ? null : "color: orange"},
  ]
  if (item.pluginId !== undefined) {
    lines.push({name: "pluginId", value: item.pluginId})
  }
  lines.push(
    {name: "sourceModule", value: item.sourceModule},
    {name: "descriptor", value: shortenPath(item.descriptor), hint: item.descriptor},
  )
  return buildTooltip(lines)
}

function buildTooltip(lines) {
  let result = ""
  for (const line of lines) {
    if (line.main) {
      result += `<span class="tooltipMainName">${line.name}</span>`
    }
    else {
      result += `<br/><span style="user-select: none">${line.name}</span>`
    }
    const valueStyleClass = "tooltipValue"
    if (line.value != null) {
      result += `<span class="${valueStyleClass}"`
      if (line.extraStyle != null && line.extraStyle.length > 0) {
        result += ` style="${line.extraStyle}"`
      }
      if (line.hint != null && line.hint.length !== 0) {
        result += ` title="${line.hint}"`
      }
      result += `>${line.value}</span>`
    }
  }
  return result
}

function shortenPath(p) {
  const prefix = "plugins/"
  if (p.startsWith(prefix)) {
    p = p.substring(prefix.length)
  }
  return p
    .replace("/resources/META-INF/", " ")
    .replace("/src/main/resources/", " ")
    .replace("/META-INF/", " ")
    .replace("/resources/", " ")
    .replace("/java/src/main/", " ")
    .replace("/src/main/", " ")
    .replace("/src/", " ")
}