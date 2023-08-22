<#
   The script adds paths, given as parameters, to the Microsoft Defender folder exclusion list,
   unless they are already excluded.
#>

#Requires -RunAsAdministrator

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if ($args.Count -eq 0) {
  Write-Host "usage: $PSCommandPath path [path ...]"
  exit 1
}

try {
  Import-Module Defender

  # returns `$true` when a path is already covered by the exclusion list
  function Test-Excluded ([string] $path, [string[]] $exclusions) {
    foreach ($exclusion in $exclusions) {
      $expanded = [System.Environment]::ExpandEnvironmentVariables($exclusion)
      $resolvedPaths = Resolve-Path -Path $expanded
      foreach ($resolved in $resolvedPaths) {
        $resolvedStr = $resolved.ProviderPath.ToString()
        if ([cultureinfo]::InvariantCulture.CompareInfo.IsPrefix($path, $resolvedStr, @("IgnoreCase"))) {
          return $true
        }
      }
    }

    return $false
  }

  $exclusions = (Get-MpPreference).ExclusionPath
  if (-not $exclusions) {
    $exclusions = @()
  }

  foreach ($path in $args) {
    if (-not (Test-Excluded $path $exclusions)) {
      $exclusions += $path
      Write-Host "added: $path"
    } else {
      Write-Host "skipped: $path"
    }
  }

  Set-MpPreference -ExclusionPath $exclusions
} catch {
  Write-Host $_.Exception.Message
  Write-Host $_.ScriptStackTrace
  exit 1
}
