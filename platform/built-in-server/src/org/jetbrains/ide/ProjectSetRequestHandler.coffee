###
  @apiDefine OpenProjectSetRequestExample

  @apiExample {json} Request-Example:
{
  "vcs": {
    "git": {"url": "https://github.com/JetBrains/idea-templates.git"}
  },
  "project": "/spring/SpringApp"
}
###

###
  @apiDefine OpenProjectSetRequestExampleMulti

  @apiExample {json} Request-Example (multi-repository):
{
  "vcs": {
    "git": [
      {
        "url": "git@git.labs.intellij.net:idea/community"
      },
      {
        "url": "git://git.jetbrains.org/idea/android.git",
        "targetDir": "android"
      },
      {
        "url": "git://git.jetbrains.org/idea/adt-tools-base.git",
        "targetDir": "android/tools-base"
      }
    ]
  },
  "project": ""
}
###