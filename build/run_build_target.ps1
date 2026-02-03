param(
  [Parameter(Mandatory = $true, Position = 0)]
  [string]$root,

  [Parameter(Mandatory = $true, Position = 1)]
  [string]$build_target,

  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$rest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# See how Bazel java wrapper works
# Here we pass arguments to it in the following way:
# - "--debug" becomes "--debug"
# - everything else becomes "--jvm-arg=<value>"
$ARGS = @()
foreach ($arg in $rest) {
  if ($arg -eq '--debug') {
    $ARGS += '--debug'
  } else {
    $ARGS += "--jvm_flag=$arg"
  }
}

Push-Location $root
try {
  & (Join-Path $root 'bazel.cmd') run $build_target -- @ARGS
  $exitCode = $LASTEXITCODE
} finally {
  Pop-Location
}

exit $exitCode
